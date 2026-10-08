import { fetchEventSource } from '@microsoft/fetch-event-source'
import { handleUnauthorized } from '@/api/request'
import { conversationApi } from '@/api/modules/conversation'
import { useMessageStore } from '@/stores/message'
import { tokenStorage } from '@/utils/storage'
import { BizError } from '@/utils/BizError'
import type {
  RagCitation,
  SseDeltaEvent,
  SseDoneEvent,
  SseErrorEvent,
  SseMetaEvent,
  SseToolEvent,
  ToolCallInfo,
} from '@/types/api'

/** delta 渲染节流间隔: 先入缓冲, 批量 flush, 避免每个 token 都触发 DOM 更新 */
const FLUSH_INTERVAL_MS = 50

/**
 * SSE 流式聊天 composable（契约二, SSE 连接的唯一存在处）
 *
 * 职责边界: 建连 → 四事件分发 → 节流写入 message store → 结束清理;
 * store 只存状态, 不碰连接; 组件只调 send/stop, 不碰事件。
 *
 * 关键约定（评审锁死）:
 * - onerror 必须 throw: fetch-event-source 默认断线重连会再次 POST, 导致 USER 消息重复落库
 * - done/error 后服务端 complete, 这里主动 abort 防库默认的"连接关闭即重连"
 * - 停止生成 = 调停止接口(后端断开 AI 调用) + abort(), 消息置 STOPPED 保留已生成文本
 */
export function useSseChat() {
  const messageStore = useMessageStore()

  let controller: AbortController | null = null
  let flushTimer: number | null = null
  let deltaBuffer = ''
  /** 当前流所属会话: 停止时通知后端取消上游 AI 调用 */
  let activeConversationId: string | null = null
  /** 已收到终态事件（done/error）, 用于区分「主动/收尾 abort」与「异常中断」 */
  let finished = false

  /** 缓冲的 delta 批量写入 store */
  function flushDelta(): void {
    if (deltaBuffer) {
      messageStore.appendDelta(deltaBuffer)
      deltaBuffer = ''
    }
  }

  function startFlushTimer(): void {
    if (flushTimer === null) {
      flushTimer = window.setInterval(flushDelta, FLUSH_INTERVAL_MS)
    }
  }

  /** 连接收尾: 停节流器 + flush 残留 buffer */
  function cleanup(): void {
    if (flushTimer !== null) {
      window.clearInterval(flushTimer)
      flushTimer = null
    }
    flushDelta()
    controller = null
    activeConversationId = null
  }

  /**
   * SSE 连接的公共骨架: 建连 → 事件分发 → 节流写入 store
   * meta 事件由调用方决定落库方式 (send 换 USER id, regenerate 换占位 id);
   * citations/tool 由调用方挂到当前 ASSISTANT 消息 (meta 已到, id 为真实 id)
   */
  async function runStream(
    url: string,
    body: string | undefined,
    onMeta: (meta: SseMetaEvent) => void,
    onCitations: (citations: RagCitation[]) => void,
    onTool?: (info: ToolCallInfo) => void,
  ): Promise<void> {
    const signal = controller!.signal
    try {
      await fetchEventSource(url, {
        method: 'POST',
        headers: {
          ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}),
          Authorization: `Bearer ${tokenStorage.get() ?? ''}`,
        },
        body,
        signal,
        // 后台标签页不断开, 避免切 tab 触发意外重连（契约二 2.3）
        openWhenHidden: true,

        async onopen(response) {
          const type = response.headers.get('content-type') ?? ''
          if (response.ok && type.includes('text/event-stream')) return
          // 业务异常（40400/42200/40100…）走普通 JSON: 解析后按 BizError 上抛
          const result = await response.json().catch(() => null)
          const code = result?.code ?? 50000
          if (code === 40100) {
            handleUnauthorized()
          }
          throw new BizError(code, result?.message ?? '发送失败，请稍后重试')
        },

        onmessage(event) {
          switch (event.event) {
            case 'meta': {
              onMeta(JSON.parse(event.data) as SseMetaEvent)
              startFlushTimer()
              break
            }
            case 'citations': {
              // RAG 命中片段 (meta 之后、delta 之前), 挂到当前 ASSISTANT 消息
              const payload = JSON.parse(event.data) as { citations: RagCitation[] }
              onCitations(payload.citations ?? [])
              break
            }
            case 'tool': {
              // agent 工具执行回执 (流中可出现多次), 记录到 ASSISTANT 气泡的工具轨迹
              const info = JSON.parse(event.data) as SseToolEvent
              onTool?.({ name: info.name, arguments: info.arguments, result: info.result })
              break
            }
            case 'delta': {
              const delta = JSON.parse(event.data) as SseDeltaEvent
              deltaBuffer += delta.content
              break
            }
            case 'done': {
              const done = JSON.parse(event.data) as SseDoneEvent
              finished = true
              flushDelta()
              messageStore.finishAssistant(done)
              // 服务端已 complete, 主动断开防止库默认重连
              controller?.abort()
              break
            }
            case 'error': {
              // LLM 异常（51001~51004）: 气泡置 FAILED 并展示真实原因, 连接由服务端收尾
              const err = JSON.parse(event.data) as SseErrorEvent
              finished = true
              flushDelta()
              messageStore.failAssistant(err.message)
              controller?.abort()
              break
            }
          }
        },

        onerror(err) {
          // 锁死: 必须 throw 阻止自动重连（重连 = 再次 POST = USER 消息重复落库）
          throw err
        },
      })
    } catch (e) {
      // 主动 abort（停止/收尾）时 fetch 会以 AbortError 结束, 属正常路径
      if (!finished && !signal.aborted) {
        flushDelta()
        // 原生网络异常 (TypeError 等) 收敛为 BizError, 调用方统一按业务错误处理
        const bizError = e instanceof BizError ? e : new BizError(50000, '网络连接中断，请稍后重试')
        // meta 未到达时（如 40400/42200 业务拒绝）服务端未落库, 需回滚本地乐观插入的消息
        messageStore.failOrRollback(bizError.message)
        cleanup()
        throw bizError
      }
    }
    cleanup()
  }

  /**
   * 发送消息并建立 SSE 连接
   * @throws BizError 业务失败（40100/40400/42200 等 JSON 响应）时上抛给调用方展示
   */
  async function send(conversationId: string, content: string): Promise<void> {
    if (messageStore.streaming) return

    messageStore.appendUserMessage(conversationId, content)
    finished = false
    activeConversationId = conversationId
    controller = new AbortController()
    let assistantId = ''

    await runStream(
      `/api/conversations/${conversationId}/chat`,
      JSON.stringify({ content, clientMessageId: crypto.randomUUID() }),
      (meta) => {
        assistantId = meta.assistantMessageId
        messageStore.confirmMeta(meta.userMessageId, meta.assistantMessageId)
      },
      (citations) => messageStore.setCitations(assistantId, citations),
      (info) => messageStore.attachToolCall(assistantId, info),
    )
  }

  /**
   * 重新生成最后一条回复: 复用 SSE 链路, 乐观插入 ASSISTANT 占位（不重复发 USER 消息）
   * 新版本由后端挂靠到最后一条 USER 消息, 旧版本保留供切换查看
   */
  async function regenerate(conversationId: string): Promise<void> {
    if (messageStore.streaming) return

    messageStore.beginRegenerate(conversationId)
    finished = false
    activeConversationId = conversationId
    controller = new AbortController()
    let assistantId = ''

    await runStream(
      `/api/conversations/${conversationId}/regenerate`,
      undefined,
      (meta) => {
        assistantId = meta.assistantMessageId
        messageStore.confirmRegenerateMeta(meta.userMessageId, meta.assistantMessageId)
      },
      (citations) => messageStore.setCitations(assistantId, citations),
      (info) => messageStore.attachToolCall(assistantId, info),
    )
  }

  /** 停止生成: 通知后端断开 AI 调用 → 本地断连 → 置 STOPPED */
  function stop(): void {
    if (!messageStore.streaming || finished) return
    finished = true
    // 后端取消失败不阻塞本地停止; 后端断连检测仍会兜底置 STOPPED
    if (activeConversationId) {
      conversationApi.stopGeneration(activeConversationId).catch(() => {})
    }
    controller?.abort()
    cleanup()
    messageStore.stopAssistant()
  }

  return { send, regenerate, stop }
}
