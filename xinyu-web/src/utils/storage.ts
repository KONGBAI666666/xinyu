import type { UserVO } from '@/types/api'

/**
 * 本地存储统一封装 —— 全项目唯一 localStorage 出入口
 *
 * 禁止任何页面/store 直接调 localStorage:
 * 将来换 sessionStorage / httpOnly cookie 只改这一个文件（契约 D3）
 */

const TOKEN_KEY = 'xinyu_token'
const USER_KEY = 'xinyu_user'

export const tokenStorage = {
  get(): string | null {
    return localStorage.getItem(TOKEN_KEY)
  },
  set(token: string): void {
    localStorage.setItem(TOKEN_KEY, token)
  },
  remove(): void {
    localStorage.removeItem(TOKEN_KEY)
  },
}

/** 用户信息缓存: 刷新页面后先回填, 再由 fetchMe 校准 */
export const userStorage = {
  get(): UserVO | null {
    const raw = localStorage.getItem(USER_KEY)
    if (!raw) return null
    try {
      return JSON.parse(raw) as UserVO
    } catch {
      // 缓存损坏按未缓存处理, 由 fetchMe 重新拉取
      localStorage.removeItem(USER_KEY)
      return null
    }
  },
  set(user: UserVO): void {
    localStorage.setItem(USER_KEY, JSON.stringify(user))
  },
  remove(): void {
    localStorage.removeItem(USER_KEY)
  },
}
