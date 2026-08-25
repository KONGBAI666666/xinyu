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
  async function create(characterId: string, title?: string, kbId?: string | null): Promise<ConversationVO> {
    const conversation = await conversationApi.createConversation({ characterId, title, kbId: kbId ?? null })
    list.value.unshift(conversation)
    activeId.value = conversation.id
    return conversation
  }

  /** 切换当前会话 */
  function setActive(id: string): void {
    activeId.value = id
  }

  /**
   * 删除会话: 调后端逻辑删除, 从列表移除;
   * 若删的是当前会话则清空 activeId（ChatView 的 watch 会自动 stop + clear 消息）
   */
  async function remove(id: string): Promise<void> {
    await conversationApi.deleteConversation(id)
    list.value = list.value.filter((c) => c.id !== id)
    if (activeId.value === id) {
      activeId.value = null
    }
  }

  /**
   * 重命名会话: 调后端更新标题, 同时更新本地列表
   */
  async function updateTitle(id: string, title: string): Promise<void> {
    const updated = await conversationApi.renameConversation(id, title)
    const idx = list.value.findIndex((c) => c.id === id)
    if (idx !== -1) {
      list.value[idx] = updated
    }
  }

  return { list, activeId, loading, active, fetchList, create, setActive, remove, updateTitle }
})
