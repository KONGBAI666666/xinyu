import axios, { type AxiosRequestConfig } from 'axios'
import { BizError } from '@/utils/BizError'
import { tokenStorage, userStorage } from '@/utils/storage'
import type { ApiResult } from '@/types/api'

/**
 * axios 实例（契约一 1.1/1.2）
 *
 * 统一约定:
 * - 请求自动携带 Authorization: Bearer <token>
 * - 响应 code !== 0 统一 throw BizError, 页面层只写成功分支
 * - 40100 全局兜底: 清凭证跳登录（登录页内不跳, 只展示 message）
 *
 * 这里不 import router 而用 location 跳转:
 * 避免 request → router → guards → store → request 的循环依赖,
 * 且全页刷新可顺带清空内存中的过期状态
 */
export const instance = axios.create({
  baseURL: '/api',
  timeout: 15_000,
})

instance.interceptors.request.use((config) => {
  const token = tokenStorage.get()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

instance.interceptors.response.use(
  (response) => {
    const res = response.data as ApiResult<unknown>
    if (res.code === 0) {
      // 拆掉 Result 包装, 调用方直接拿业务数据
      return res.data as never
    }
    if (res.code === 40100) {
      handleUnauthorized()
    }
    throw new BizError(res.code, res.message)
  },
  () => {
    // 网络层错误（超时/断网/5xx）: 统一收敛为 50000
    throw new BizError(50000, '网络开小差了，请稍后重试')
  },
)

/** token 失效兜底: 清凭证并带回跳参数去登录页（SSE 通道收到 40100 时同样复用） */
export function handleUnauthorized(): void {
  tokenStorage.remove()
  userStorage.remove()
  if (window.location.pathname !== '/login') {
    const redirect = encodeURIComponent(window.location.pathname + window.location.search)
    window.location.href = `/login?redirect=${redirect}`
  }
}

/** 类型化请求方法: 返回值即为 Result.data */
export function get<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
  return instance.get(url, config) as Promise<T>
}

export function post<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
  return instance.post(url, data, config) as Promise<T>
}

export function put<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
  return instance.put(url, data, config) as Promise<T>
}

export function del<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
  return instance.delete(url, config) as Promise<T>
}
