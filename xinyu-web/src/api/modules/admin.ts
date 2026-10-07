import { get, put } from '@/api/request'
import type { AdminCharacterVO, AdminUserVO, PlatformOverviewVO } from '@/types/api'

/**
 * 管理后台接口 (后端 require_admin 统一把守, role=ADMIN 才可访问)
 */
export const adminApi = {
  /** 按用户名精确查找用户 (v1 无分页列表) */
  findUser(username: string): Promise<AdminUserVO | null> {
    return get('/admin/users/by-username', { params: { username } })
  },

  /** 封禁/解封用户 */
  setUserStatus(userId: string, status: 'ACTIVE' | 'BANNED'): Promise<AdminUserVO> {
    return put(`/admin/users/${userId}/status`, { status })
  },

  /** 角色审核队列分页 (页满即视为可能还有更多, 用 offset 续拉) */
  listCharacters(status: string | null, offset: number, size: number): Promise<AdminCharacterVO[]> {
    const params: Record<string, string | number> = { offset, size }
    if (status) params.status = status
    return get('/admin/characters', { params })
  },

  /** 审核动作: 通过发布 / 下架 */
  reviewCharacter(characterId: string, status: 'PUBLISHED' | 'OFFLINE'): Promise<AdminCharacterVO> {
    return put(`/admin/characters/${characterId}/status`, { status })
  },

  /** 平台概览 */
  overview(): Promise<PlatformOverviewVO> {
    return get('/admin/overview')
  },
}
