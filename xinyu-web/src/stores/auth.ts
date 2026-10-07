import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { authApi } from '@/api/modules/auth'
import { tokenStorage, userStorage } from '@/utils/storage'
import type { AuthResponse, LoginDTO, RegisterDTO, UserVO } from '@/types/api'

/** 解析 JWT payload 中的 exp (秒级时间戳), 不可解析返回 null (不阻断登录态) */
function getTokenExp(tokenValue: string): number | null {
  try {
    const payload = tokenValue.split('.')[1]
    if (!payload) return null
    const base64 = payload.replace(/-/g, '+').replace(/_/g, '/')
    const json = decodeURIComponent(
      atob(base64)
        .split('')
        .map((c) => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2))
        .join(''),
    )
    const exp: unknown = JSON.parse(json).exp
    return typeof exp === 'number' ? exp : null
  } catch {
    return null
  }
}

/**
 * 认证状态（契约三 3.1 auth store）
 *
 * 持久化策略: token + user 存 localStorage（经 storage.ts 封装）,
 * store 初始化时回填, 刷新页面即恢复登录态;
 * token 过期: 路由守卫本地校验 exp + 响应拦截器 40100 双重兜底
 */
export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(tokenStorage.get())
  const user = ref<UserVO | null>(userStorage.get())

  const isLoggedIn = computed(() => !!token.value)

  /** token 是否存在且未过期（每次调用实时判断, 供路由守卫使用） */
  function isTokenValid(): boolean {
    if (!token.value) return false
    const exp = getTokenExp(token.value)
    // 无法解析过期时间时不拦截, 交给后端 40100 兜底
    return exp === null || exp * 1000 > Date.now()
  }

  /** 登录/注册成功后统一落地凭证 */
  function applyAuth(auth: AuthResponse): void {
    token.value = auth.token
    user.value = auth.user
    tokenStorage.set(auth.token)
    userStorage.set(auth.user)
  }

  async function register(dto: RegisterDTO): Promise<void> {
    applyAuth(await authApi.register(dto))
  }

  async function login(dto: LoginDTO): Promise<void> {
    applyAuth(await authApi.login(dto))
  }

  /** 刷新页面后校准用户信息（token 失效时由拦截器兜底跳登录） */
  async function fetchMe(): Promise<void> {
    user.value = await authApi.fetchMe()
    userStorage.set(user.value)
  }

  /** 退出 = 前端删凭证（无后端接口, JWT 无状态） */
  function logout(): void {
    token.value = null
    user.value = null
    tokenStorage.remove()
    userStorage.remove()
  }

  return { token, user, isLoggedIn, isTokenValid, register, login, fetchMe, logout }
})
