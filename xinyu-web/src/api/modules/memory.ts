import { get, put, del } from '@/api/request'
import type { MemoryUpdateDTO, MemoryVO } from '@/types/api'

/**
 * 记忆管理接口（用户视角: 列表 / 编辑 / 删除）
 *
 * 不暴露新增: 记忆由服务端在每轮对话后异步提取, 用户只能管理已有记忆。
 */
export const memoriesApi = {
  /** 列出当前用户的记忆, 可按角色筛选 */
  list(characterId?: string): Promise<MemoryVO[]> {
    const query = characterId ? `?characterId=${characterId}` : ''
    return get(`/memories${query}`)
  },

  /** 编辑记忆（content / importance / status） */
  update(id: string, data: MemoryUpdateDTO): Promise<MemoryVO> {
    return put(`/memories/${id}`, data)
  },

  /** 删除记忆 */
  remove(id: string): Promise<void> {
    return del(`/memories/${id}`)
  },
}
