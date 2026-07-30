import { ref } from 'vue'
import { defineStore } from 'pinia'
import { conversationApi } from '@/api/modules/conversation'
import type { MessageVO } from '@/types/api'

/** 历史消息每页条数（后端缺省 20, 上限 100） */
const PAGE_SIZE = 20

/**
 * 当前会话消息状态（契约三 3.1 message store）
 *
 * M1-4.3 仅历史加载 + 发送后的本地乐观插入;
 * streaming / confirmMeta / appendDelta 等 SSE 状态随 M1-4.4 补充,
 * loadEarlier（向上翻页）随消息滚动加载一并接入
 */
export const useMessageStore = defineStore('message', () => {
  /** 当前会话消息, 升序（旧→新） */
  const items = ref<MessageVO[]>([])
  const hasMore = ref(false)
  const loadingHistory = ref(false)
  /** 已发送、等待 AI 回复（M1-4.4 将被 streaming 状态取代） */
  const awaitingReply = ref(false)

  /** 加载最新一页历史 */
  async function load(conversationId: string): Promise<void> {
    loadingHistory.value = true
    try {
      const page = await conversationApi.fetchMessages(conversationId, { size: PAGE_SIZE })
      items.value = page
      hasMore.value = page.length >= PAGE_SIZE
    } finally {
      loadingHistory.value = false
    }
  }

  /** 发送瞬间乐观插入本地 USER 消息（临时 id, M1-4.4 由 SSE meta 换真实 id） */
  function appendUserMessage(conversationId: string, content: string): void {
    const last = items.value[items.value.length - 1]
    items.value.push({
      id: `local-${Date.now()}`,
      conversationId,
      sequenceNo: (last?.sequenceNo ?? 0) + 1,
      messageType: 'USER',
      content,
      status: 'COMPLETED',
      completionTokens: null,
      createdAt: new Date().toISOString().slice(0, 19),
    })
    awaitingReply.value = true
  }

  /** 切换会话前清空, 避免旧会话消息闪现 */
  function clear(): void {
    items.value = []
    hasMore.value = false
    awaitingReply.value = false
  }

  return { items, hasMore, loadingHistory, awaitingReply, load, appendUserMessage, clear }
})
