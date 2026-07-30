import { get, post } from '@/api/request'
import type { ConversationVO, CreateConversationDTO, MessageVO } from '@/types/api'

/**
 * 会话/消息接口（契约一 1.3, 需登录）
 * SSE 聊天接口不在此处: fetch-event-source 于 M1-4.4 在 useSseChat 中接入
 */
export const conversationApi = {
  /** 会话列表: 仅本人, 按 last_message_at 倒序 */
  fetchConversations(): Promise<ConversationVO[]> {
    return get('/conversations')
  },

  /** 创建会话: 后端同时落库角色 greeting 消息; 40400 = 角色不存在 */
  createConversation(dto: CreateConversationDTO): Promise<ConversationVO> {
    return post('/conversations', dto)
  },

  /** 历史消息: 游标分页, 返回升序（旧→新）; before 缺省取最新一页 */
  fetchMessages(conversationId: string, params?: { before?: string; size?: number }): Promise<MessageVO[]> {
    return get(`/conversations/${conversationId}/messages`, { params })
  },
}
