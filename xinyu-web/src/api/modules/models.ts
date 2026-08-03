import { get, post, put, del } from '@/api/request'
import type { AiModelSaveDTO, AiModelVO } from '@/types/api'

/**
 * 模型管理接口（用户级 CRUD + 设默认）
 */
export const modelsApi = {
  list(): Promise<AiModelVO[]> {
    return get('/models')
  },

  create(data: AiModelSaveDTO): Promise<AiModelVO> {
    return post('/models', data)
  },

  update(id: string, data: AiModelSaveDTO): Promise<AiModelVO> {
    return put(`/models/${id}`, data)
  },

  remove(id: string): Promise<void> {
    return del(`/models/${id}`)
  },

  setDefault(id: string): Promise<void> {
    return put(`/models/${id}/default`)
  },

  /** 切换会话使用的模型（modelId 不传 = 回到用户默认模型） */
  switchConversationModel(conversationId: string, modelId: string | null): Promise<void> {
    const query = modelId ? `?modelId=${modelId}` : ''
    return put(`/conversations/${conversationId}/model${query}`)
  },
}
