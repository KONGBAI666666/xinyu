"""聊天编排服务 — 对应 Java ChatService + ContextAssembler + xinyu-ai /ai/chat/stream 合并

SSE 主链路 (单体化后不再有 Java→Python HTTP 边界, 全进程内编排):
    请求阶段: 鉴权 → USER消息落库 → ASSISTANT占位(GENERATING) → 组装上下文 → 提交
    流式阶段: meta → RAG检索注入 → LLM流式调用 → delta* → done(COMPLETED) | error(FAILED)
              前端断连/停止/超时 → STOPPED (保留已生成文本)

事件协议与前端 useSseChat 契约一致: meta(双消息ID) → delta*(增量) → done | error。
"""

import asyncio
import json
import logging

from fastapi.responses import StreamingResponse
from sqlalchemy.exc import IntegrityError
from sqlalchemy.ext.asyncio import AsyncSession

from app.ai.llm.client import LlmClient, MockLlmClient
from app.ai.memory.extractor import MemoryExtractor
from app.ai.rag.rag_service import get_rag_service
from app.ai.types import ChatMessage, ModelConfig
from app.core.config import settings
from app.core.database import SessionFactory
from app.core.exceptions import BizException, ResultCode
from app.core.security import now_local
from app.models import AiCharacter, Conversation, Message
from app.repositories import character_repo, conversation_repo, message_repo
from app.schemas.conversation import ChatRequestDTO, ConversationCreateDTO, ConversationVO
from app.schemas.message import MessageVO
from app.services import conversation_service, memory_service, model_service

logger = logging.getLogger("xinyu.chat")

# 上下文窗口: 最近 20 条消息（约 10 轮对话）
MAX_CONTEXT_MESSAGES = 20
# SSE 整体超时 (与 Java 版 180s 一致)
SSE_TIMEOUT_SECONDS = 180
# 记忆提取时最多回看的消息条数（控制 prompt 长度 + 成本）
RECENT_WINDOW = 10

# 进行中的生成: conversationId → 取消标志 (每会话同时至多一个流)
_active_streams: dict[int, asyncio.Event] = {}


# ==================== VO 映射 ====================


def _message_to_vo(m: Message) -> MessageVO:
    return MessageVO(
        id=str(m.id),
        conversationId=str(m.conversation_id),
        sequenceNo=m.sequence_no,
        messageType=m.message_type,  # type: ignore[arg-type]
        content=m.content or "",
        status=m.status,  # type: ignore[arg-type]
        promptTokens=m.prompt_tokens,
        completionTokens=m.completion_tokens,
        modelCode=m.model_code,
        createdAt=m.created_at,
    )


def _sse_event(event: str, data) -> str:
    """格式化 SSE 帧 (与前端 fetch-event-source 解析约定一致)"""
    payload = data if isinstance(data, dict) else data.model_dump(mode="json")
    return f"event:{event}\ndata:{json.dumps(payload, ensure_ascii=False)}\n\n"


# ==================== 会话创建 / 历史查询 ====================


async def create_conversation(db: AsyncSession, user_id: int, dto: ConversationCreateDTO) -> ConversationVO:
    """创建会话, 同时写入角色 greeting 作为首条 ASSISTANT 消息"""
    character = await character_repo.get_by_id(db, int(dto.characterId))
    chattable = character is not None and (
        (character.creator_type == "USER" and character.creator_id == user_id)
        or (character.creator_type == "OFFICIAL" and character.status == "PUBLISHED")
    )
    if not chattable:
        raise BizException(ResultCode.NOT_FOUND, "角色不存在或不可用")

    conversation = Conversation(
        user_id=user_id,
        character_id=character.id,
        kb_id=int(dto.kbId) if dto.kbId else None,
        title=dto.title.strip() if dto.title and dto.title.strip() else character.name,
        last_message_at=now_local(),
        last_message_preview=character.greeting,
    )
    await conversation_repo.insert(db, conversation)

    greeting = Message(
        conversation_id=conversation.id,
        user_id=user_id,
        sequence_no=1,
        message_type="ASSISTANT",
        content=character.greeting,
        status="COMPLETED",
    )
    await message_repo.insert(db, greeting)
    await db.commit()
    return conversation_service.to_vo(conversation)


async def list_messages(
    db: AsyncSession, user_id: int, conversation_id: int, before: int | None, size: int | None
) -> list[MessageVO]:
    """游标分页查消息历史（升序返回）; before 为空取最新一页"""
    await conversation_service.require_owned(db, conversation_id, user_id)
    page_size = 20 if size is None else min(max(size, 1), 100)
    messages = await message_repo.list_history(db, conversation_id, before, page_size)
    return [_message_to_vo(m) for m in messages]


# ==================== SSE 流式聊天 ====================


async def chat(db: AsyncSession, user_id: int, conversation_id: int, dto: ChatRequestDTO) -> StreamingResponse:
    """SSE 流式聊天: 落库 USER 消息与 ASSISTANT 占位 → 异步调 LLM 逐段推送"""
    conversation = await conversation_service.require_owned(db, conversation_id, user_id)
    character = await character_repo.get_by_id(db, conversation.character_id)
    if character is None:
        raise BizException(ResultCode.NOT_FOUND, "角色不存在")

    # 解析模型配置 (解密 API Key)
    model_config = await _resolve_model_config(db, conversation, user_id)

    # 1. USER 消息落库 (client_message_id 唯一约束 → 重复请求幂等拒绝)
    seq = await message_repo.next_sequence_no(db, conversation_id)
    user_msg = Message(
        conversation_id=conversation_id,
        user_id=user_id,
        sequence_no=seq,
        client_message_id=dto.clientMessageId,
        message_type="USER",
        content=dto.content,
        status="COMPLETED",
    )
    try:
        await message_repo.insert(db, user_msg)
    except IntegrityError:
        await db.rollback()
        raise BizException(ResultCode.PARAM_ERROR, "重复的消息请求") from None

    # 标题保持为创建时的命名 (默认角色名), 用户可通过重命名接口自定义
    await conversation_repo.refresh_last_message(db, conversation_id, dto.content, now_local())

    # 2. ASSISTANT 占位 (GENERATING)
    assistant_msg = Message(
        conversation_id=conversation_id,
        user_id=user_id,
        sequence_no=seq + 1,
        parent_message_id=user_msg.id,
        message_type="ASSISTANT",
        content="",
        status="GENERATING",
        model_code=model_config.modelCode,
    )
    await message_repo.insert(db, assistant_msg)

    # 3. 组装上下文 (system prompt + 记忆 + 历史; RAG 块在流式阶段检索后注入)
    memory_block = await memory_service.build_memory_block(db, user_id, conversation.character_id)
    history = await message_repo.list_history(db, conversation_id, None, MAX_CONTEXT_MESSAGES)
    context = _assemble_context(character, memory_block, history)

    # 前置落库全部提交, 流式阶段改用独立会话写库
    await db.commit()

    # 停止标志先登记再返回响应: 停止请求早于流启动到达时也能生效
    cancel = asyncio.Event()
    _active_streams[conversation_id] = cancel

    # RAG 参数: 会话绑定了知识库时检索; 必须使用入库时锁定的 Embedding 模型
    rag_kb_id = str(conversation.kb_id) if conversation.kb_id else None
    rag_embedding_model = None
    if conversation.kb_id:
        from app.services import knowledge_service

        rag_embedding_model = await knowledge_service.get_embedding_model(db, conversation.kb_id)

    return StreamingResponse(
        _event_stream(
            conversation_id=conversation_id,
            user_id=user_id,
            character_id=conversation.character_id,
            user_msg_id=user_msg.id,
            assistant_msg_id=assistant_msg.id,
            context=context,
            model_config=model_config,
            rag_kb_id=rag_kb_id,
            rag_embedding_model=rag_embedding_model,
            user_query=dto.content,
            temperature=float(character.temperature) if character.temperature is not None else 0.8,
            max_tokens=character.max_tokens if character.max_tokens is not None else 1024,
            cancel=cancel,
        ),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "Connection": "keep-alive",
            "X-Accel-Buffering": "no",
        },
    )


async def stop_generation(db: AsyncSession, user_id: int, conversation_id: int) -> None:
    """停止会话当前生成: 取消上游 LLM 流, 消息置 STOPPED 并保留已生成文本

    幂等: 无进行中的生成时直接成功返回。
    """
    await conversation_service.require_owned(db, conversation_id, user_id)
    cancel = _active_streams.get(conversation_id)
    if cancel is not None:
        cancel.set()
    # 无进行中流: 可能已完成或尚未建立连接, 幂等成功


# ==================== 内部编排 ====================


async def _resolve_model_config(db: AsyncSession, conversation: Conversation, user_id: int) -> ModelConfig:
    """解析模型配置: 会话级覆盖 → 用户默认 → dev 降级 mock / prod 报错"""
    if conversation.model_id is not None:
        return await model_service.resolve_config(db, conversation.model_id, user_id)
    default_config = await model_service.resolve_default_config(db, user_id)
    if default_config is not None:
        return default_config
    if settings.dev_mode:
        # dev 环境: 传 dummy 配置, Mock 客户端兜底
        return ModelConfig(modelCode="mock", baseUrl="mock", apiKey="mock")
    raise BizException(ResultCode.PARAM_ERROR, "尚未配置 AI 模型, 请先在「模型管理」添加一个模型")


def _assemble_context(character: AiCharacter, memory_block: str, history: list[Message]) -> list[ChatMessage]:
    """上下文组装: 角色 System Prompt + [记忆块] + 最近 N 条历史"""
    system_prompt = character.system_prompt
    if memory_block:
        system_prompt = system_prompt + memory_block
    messages = [ChatMessage.system(system_prompt)]

    for msg in history:
        if msg.message_type == "USER":
            messages.append(ChatMessage.user(msg.content or ""))
        elif msg.message_type == "ASSISTANT":
            # GENERATING 占位/FAILED 无有效文本, 不进上下文
            if msg.status in ("COMPLETED", "STOPPED") and msg.content:
                messages.append(ChatMessage.assistant(msg.content))
        # SYSTEM 类消息是站内提示, 不参与模型对话
    return messages


async def _finalize(
    assistant_msg_id: int,
    conversation_id: int,
    content: str,
    status: str,
    prompt_tokens: int | None = None,
    completion_tokens: int | None = None,
    refresh_preview: bool = True,
) -> None:
    """终态落库 (流式阶段使用独立会话); GENERATING 守卫保证停止/超时不被覆盖"""
    async with SessionFactory() as session:
        updated = await message_repo.finalize_assistant_message(
            session, assistant_msg_id, content, status, prompt_tokens, completion_tokens
        )
        # 守卫: 消息已被超时/停止收尾置 STOPPED 时不覆盖, 预览与消息体保持一致
        if updated and refresh_preview and content:
            await conversation_repo.refresh_last_message(session, conversation_id, content)
        await session.commit()


async def _event_stream(
    conversation_id: int,
    user_id: int,
    character_id: int,
    user_msg_id: int,
    assistant_msg_id: int,
    context: list[ChatMessage],
    model_config: ModelConfig,
    rag_kb_id: str | None,
    rag_embedding_model: str | None,
    user_query: str,
    temperature: float,
    max_tokens: int,
    cancel: asyncio.Event,
):
    """SSE 事件生成器: meta → RAG检索 → LLM 流式 → done/error/STOPPED 收尾"""
    generated: list[str] = []
    finished = False
    client = None
    try:
        # 1. meta: 双消息 ID 回执
        yield _sse_event("meta", {"userMessageId": str(user_msg_id), "assistantMessageId": str(assistant_msg_id)})

        # 2. RAG 检索 (会话绑定了知识库时); search 内部失败降级为空, 不阻断聊天
        messages = list(context)
        if rag_kb_id and user_query:
            _, rag_block = await get_rag_service().search(
                kb_id=rag_kb_id,
                query=user_query,
                model_config=model_config,
                embedding_model=rag_embedding_model,
            )
            # RAG 结果只追加一次到 system 消息末尾
            if rag_block and messages and messages[0].role == "system":
                first = messages[0]
                messages[0] = first.model_copy(update={"content": first.content + "\n\n" + rag_block})

        # 3. 选择 LLM 客户端 (mock 配置 = dev 无模型时的降级路径)
        if settings.mock_mode or model_config.modelCode == "mock":
            client = MockLlmClient()
        else:
            client = LlmClient(model_config)

        # 4. 流式调用 (整体超时兜底: 超时视作停止, 保留已生成文本)
        try:
            async with asyncio.timeout(SSE_TIMEOUT_SECONDS):
                async for event_type, payload in client.stream_chat(
                    messages=messages, temperature=temperature, max_tokens=max_tokens
                ):
                    if cancel.is_set():
                        break
                    if event_type == "delta":
                        generated.append(payload["content"])
                        yield _sse_event("delta", payload)
                    elif event_type == "done":
                        content = "".join(generated)
                        await _finalize(
                            assistant_msg_id,
                            conversation_id,
                            content,
                            "COMPLETED",
                            payload["promptTokens"],
                            payload["completionTokens"],
                        )
                        yield _sse_event(
                            "done",
                            {
                                "messageId": str(assistant_msg_id),
                                "promptTokens": payload["promptTokens"],
                                "completionTokens": payload["completionTokens"],
                                "status": "COMPLETED",
                            },
                        )
                        finished = True
                        # 异步触发记忆提取 (不阻塞聊天主链路)
                        _spawn_memory_extraction(user_id, character_id, conversation_id, context, model_config)
                        return
                    elif event_type == "error":
                        logger.error(
                            "AI 生成失败: assistantMessageId=%s, code=%s, msg=%s",
                            assistant_msg_id, payload.get("code"), payload.get("message"),
                        )
                        await _finalize(assistant_msg_id, conversation_id, "".join(generated), "FAILED")
                        yield _sse_event("error", payload)
                        finished = True
                        return
        except TimeoutError:
            logger.info("SSE 超时, 消息置 STOPPED: assistantMessageId=%s", assistant_msg_id)

        # 取消/超时收尾: 消息仍为 GENERATING 时置 STOPPED (若流恰好自然结束, 守卫保证幂等)
        if not finished:
            logger.info("生成已停止: assistantMessageId=%s", assistant_msg_id)
            await _finalize(assistant_msg_id, conversation_id, "".join(generated), "STOPPED")
    except asyncio.CancelledError:
        # 客户端断连 (AbortController/停止生成): 消息置 STOPPED 保留已生成文本
        logger.info("SSE 客户端断连, 消息置 STOPPED: assistantMessageId=%s", assistant_msg_id)
        try:
            await _finalize(assistant_msg_id, conversation_id, "".join(generated), "STOPPED")
        except Exception:
            logger.exception("断连收尾失败: assistantMessageId=%s", assistant_msg_id)
        raise
    finally:
        # 无论正常结束还是断连, 都释放 LLM 连接
        if client is not None:
            try:
                await client.aclose()
            except Exception:
                pass
        # 条件移除: 不误删同会话后续新流的标志
        if _active_streams.get(conversation_id) is cancel:
            _active_streams.pop(conversation_id, None)


def _spawn_memory_extraction(
    user_id: int,
    character_id: int,
    conversation_id: int,
    context: list[ChatMessage],
    model_config: ModelConfig,
) -> None:
    """后台任务: LLM 提取长期记忆并落库 (失败静默忽略, 不影响聊天)"""
    asyncio.create_task(_extract_and_save(user_id, character_id, conversation_id, context, model_config))


async def _extract_and_save(
    user_id: int,
    character_id: int,
    conversation_id: int,
    context: list[ChatMessage],
    model_config: ModelConfig,
) -> None:
    # mock 配置无法真实调用 LLM, 直接跳过
    if model_config.modelCode == "mock":
        return
    # 过滤 system, 只保留 user/assistant, 最多 RECENT_WINDOW 条
    dialog = [m for m in context if m.role != "system"]
    if len(dialog) > RECENT_WINDOW:
        dialog = dialog[-RECENT_WINDOW:]
    # 至少要有 2 条（一问一答）才值得提取
    if len(dialog) < 2:
        return

    # 把对话拼成纯文本给提取模型
    dialog_text = "".join(
        f"{'用户' if m.role == 'user' else 'AI'}: {m.content}\n" for m in dialog
    )

    try:
        result = await MemoryExtractor.extract(model_config, dialog_text)
        if not result.memories:
            return
        async with SessionFactory() as session:
            await memory_service.save_extracted(user_id, character_id, conversation_id, result.memories)
            await session.commit()
        logger.info("记忆提取成功: userId=%s, characterId=%s, 条数=%s", user_id, character_id, len(result.memories))
    except Exception as e:
        logger.warning(
            "记忆提取失败（静默忽略）: userId=%s, characterId=%s, convId=%s, cause=%s",
            user_id, character_id, conversation_id, e,
        )
