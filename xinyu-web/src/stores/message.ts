import { ref } from 'vue'
import { defineStore } from 'pinia'
import { conversationApi } from '@/api/modules/conversation'
import type { MessageVO, SseDoneEvent } from '@/types/api'

/** 历史消息每页条数（后端缺省 20, 上限 100） */
const PAGE_SIZE = 20

/**
 * 当前会话消息状态（契约三 3.1 message store）
 *
 * SSE 状态唯一落点: 连接本身由 useSseChat 管理, 事件回调只调这里的 actions;
 * 流式中的 ASSISTANT 消息就是 items 末尾的 GENERATING 项, 不单独存副本。
 */
export const useMessageStore = defineStore('message', () => {
  /** 当前会话消息, 升序（旧→新） */
  const items = ref<MessageVO[]>([])
  const loadingHistory = ref(false)
  /** 是否有进行中的生成（发送按钮 ⇄ 停止按钮） */
  const streaming = ref(false)

  /** 历史加载请求序号: 快速切换会话时丢弃先发后至的过期响应 */
  let loadSeq = 0

  /** 加载最新一页历史 */
  async function load(conversationId: string): Promise<void> {
    const seq = ++loadSeq
    loadingHistory.value = true
    try {
      const page = await conversationApi.fetchMessages(conversationId, { size: PAGE_SIZE })
      if (seq !== loadSeq) return // 期间又切换了会话, 丢弃过期响应
      // 刷新时残留的 GENERATING（连接已不在）按 STOPPED 展示, 不恢复连接（契约二 2.3）
      items.value = page.map((m) =>
        m.status === 'GENERATING' ? { ...m, status: 'STOPPED' } : m,
      )
    } finally {
      if (seq === loadSeq) loadingHistory.value = false
    }
  }

  /** 发送瞬间乐观插入本地 USER 消息（临时 id, meta 到达后换真实 id） */
  function appendUserMessage(conversationId: string, content: string): void {
    const last = items.value[items.value.length - 1]
    items.value.push({
      id: `local-${Date.now()}`,
      conversationId,
      sequenceNo: (last?.sequenceNo ?? 0) + 1,
      messageType: 'USER',
      content,
      status: 'COMPLETED',
      promptTokens: null,
      completionTokens: null,
      modelCode: null,
      createdAt: new Date().toISOString().slice(0, 19),
    })
    streaming.value = true
  }

  /** meta: USER 换真实 id + 插入 ASSISTANT 占位（GENERATING, 空文本=呼吸点） */
  function confirmMeta(userMessageId: string, assistantMessageId: string): void {
    const user = items.value[items.value.length - 1]
    if (user?.messageType === 'USER') {
      user.id = userMessageId
    }
    items.value.push({
      id: assistantMessageId,
      conversationId: user?.conversationId ?? '',
      sequenceNo: (user?.sequenceNo ?? 0) + 1,
      messageType: 'ASSISTANT',
      content: '',
      status: 'GENERATING',
      promptTokens: null,
      completionTokens: null,
      modelCode: null,
      createdAt: new Date().toISOString().slice(0, 19),
    })
  }

  /** delta: 追加增量文本（useSseChat 已做 50ms 节流, 这里直接写入） */
  function appendDelta(text: string): void {
    const generating = findGenerating()
    if (generating) {
      generating.content += text
    }
  }

  /** done: → COMPLETED + tokens */
  function finishAssistant(done: SseDoneEvent): void {
    const generating = findGenerating()
    if (generating) {
      generating.status = done.status
      generating.promptTokens = done.promptTokens
      generating.completionTokens = done.completionTokens
    }
    streaming.value = false
  }

  /** error: → FAILED（后端同步置 FAILED, 已生成文本保留） */
  function failAssistant(): void {
    const generating = findGenerating()
    if (generating) {
      generating.status = 'FAILED'
    }
    streaming.value = false
  }

  /**
   * 连接失败收尾: 已有 GENERATING 占位则置 FAILED;
   * meta 未到达（服务端未落库）则回滚乐观插入的本地 USER 消息, 避免刷新后消失的"孤儿"气泡
   */
  function failOrRollback(): void {
    const generating = findGenerating()
    if (generating) {
      generating.status = 'FAILED'
    } else {
      const last = items.value[items.value.length - 1]
      if (last?.messageType === 'USER' && last.id.startsWith('local-')) {
        items.value.pop()
      }
    }
    streaming.value = false
  }

  /** 本地 abort: → STOPPED（后端检测断连后同样置 STOPPED, 两端最终一致） */
  function stopAssistant(): void {
    const generating = findGenerating()
    if (generating) {
      generating.status = 'STOPPED'
    }
    streaming.value = false
  }

  /** 切换会话前清空, 避免旧会话消息闪现 */
  function clear(): void {
    loadSeq++ // 使在途的历史加载响应作废
    items.value = []
    streaming.value = false
  }

  /** 流式中的 ASSISTANT 项（单会话串行, 至多一条） */
  function findGenerating(): MessageVO | undefined {
    const last = items.value[items.value.length - 1]
    return last?.messageType === 'ASSISTANT' && last.status === 'GENERATING' ? last : undefined
  }

  return {
    items,
    loadingHistory,
    streaming,
    load,
    appendUserMessage,
    confirmMeta,
    appendDelta,
    finishAssistant,
    failAssistant,
    failOrRollback,
    stopAssistant,
    clear,
  }
})
