import { del, get, post, put } from '@/api/request'
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

  /** 删除会话: 逻辑删除会话及关联消息 */
  deleteConversation(conversationId: string): Promise<void> {
    return del(`/conversations/${conversationId}`)
  },

  /** 重命名会话 */
  renameConversation(conversationId: string, title: string): Promise<ConversationVO> {
    return put(`/conversations/${conversationId}/title`, { title })
  },

  /** 历史消息: 游标分页, 返回升序（旧→新）; before 缺省取最新一页 */
  fetchMessages(conversationId: string, params?: { before?: string; size?: number }): Promise<MessageVO[]> {
    return get(`/conversations/${conversationId}/messages`, { params })
  },

  /** 停止当前生成: 后端断开 AI 调用, 消息置 STOPPED 保留已生成文本 (幂等) */
  stopGeneration(conversationId: string): Promise<void> {
    return post(`/conversations/${conversationId}/stop`)
  },
}
