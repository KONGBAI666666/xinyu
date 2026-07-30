import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { conversationApi } from '@/api/modules/conversation'
import type { ConversationVO } from '@/types/api'

/**
 * 会话列表状态（契约三 3.1 conversation store）
 *
 * 职责: 只存状态; 切换会话后加载消息由 ChatView 编排（watch activeId）
 * applyPreview(SSE done 后刷新列表冗余字段) 随 M1-4.4 流式聊天补充
 */
export const useConversationStore = defineStore('conversation', () => {
  const list = ref<ConversationVO[]>([])
  const activeId = ref<string | null>(null)
  const loading = ref(false)

  const active = computed(() => list.value.find((c) => c.id === activeId.value) ?? null)

  /** 拉取会话列表（后端已按 last_message_at 倒序） */
  async function fetchList(): Promise<void> {
    loading.value = true
    try {
      list.value = await conversationApi.fetchConversations()
    } finally {
      loading.value = false
    }
  }

  /** 创建会话（后端同时落库 greeting）, unshift 进列表并设为当前 */
  async function create(characterId: string, title?: string): Promise<ConversationVO> {
    const conversation = await conversationApi.createConversation({ characterId, title })
    list.value.unshift(conversation)
    activeId.value = conversation.id
    return conversation
  }

  /** 切换当前会话 */
  function setActive(id: string): void {
    activeId.value = id
  }

  return { list, activeId, loading, active, fetchList, create, setActive }
})
