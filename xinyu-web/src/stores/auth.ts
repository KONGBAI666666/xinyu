import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { authApi } from '@/api/modules/auth'
import { tokenStorage, userStorage } from '@/utils/storage'
import type { AuthResponse, LoginDTO, RegisterDTO, UserVO } from '@/types/api'

/**
 * 认证状态（契约三 3.1 auth store）
 *
 * 持久化策略: token + user 存 localStorage（经 storage.ts 封装）,
 * store 初始化时回填, 刷新页面即恢复登录态;
 * token 2h 过期不在前端计时, 由响应拦截器 40100 统一兜底
 */
export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(tokenStorage.get())
  const user = ref<UserVO | null>(userStorage.get())

  const isLoggedIn = computed(() => !!token.value)

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

  return { token, user, isLoggedIn, register, login, fetchMe, logout }
})
