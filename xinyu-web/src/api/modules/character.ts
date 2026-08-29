import { get, post, put, del } from '@/api/request'
import type { CharacterSaveDTO, CharacterVO } from '@/types/api'

/** 广场排序方式 */
export type SquareSort = 'RECOMMEND' | 'HOT' | 'LATEST'

/**
 * 角色接口
 *
 * 分两类:
 * - 广场类(square/squareDetail): 游客可访问, 仅 PUBLISHED
 * - 管理类(list/CRUD): 需登录
 * - 收藏类(favorite/unfavorite): 需登录, 游客引导登录
 */
export const charactersApi = {
  // ---------- 广场（游客可访问） ----------

  /** 广场列表: 搜索 + 三种排序 */
  square(keyword: string | null, sort: SquareSort): Promise<CharacterVO[]> {
    const params = new URLSearchParams()
    if (keyword) params.set('keyword', keyword)
    params.set('sort', sort)
    const qs = params.toString()
    return get(`/characters/square${qs ? `?${qs}` : ''}`)
  },

  /** 广场详情: 仅 PUBLISHED 角色对所有人可见 */
  squareDetail(id: string): Promise<CharacterVO | null> {
    return get(`/characters/${id}/detail`)
  },

  // ---------- 管理类（需登录） ----------

  /** 列出当前用户可见的角色 */
  list(): Promise<CharacterVO[]> {
    return get('/characters')
  },

  /** 创建角色 */
  create(data: CharacterSaveDTO): Promise<CharacterVO> {
    return post('/characters', data)
  },

  /** 编辑角色(仅自建) */
  update(id: string, data: CharacterSaveDTO): Promise<CharacterVO> {
    return put(`/characters/${id}`, data)
  },

  /** 删除角色(仅自建, 官方不可删) */
  remove(id: string): Promise<void> {
    return del(`/characters/${id}`)
  },

  /** 切换角色状态 */
  switchStatus(id: string, status: 'DRAFT' | 'PUBLISHED' | 'OFFLINE'): Promise<CharacterVO> {
    return put(`/characters/${id}/status?status=${status}`)
  },

  // ---------- 收藏（需登录） ----------

  /** 收藏角色(幂等) */
  favorite(id: string): Promise<void> {
    return post(`/characters/${id}/favorite`)
  },

  /** 取消收藏(幂等) */
  unfavorite(id: string): Promise<void> {
    return del(`/characters/${id}/favorite`)
  },
}

