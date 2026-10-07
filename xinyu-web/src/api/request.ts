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

/**
 * 把 AxiosResponse 拆成业务数据。
 *
 * axios 的类型要求响应拦截器仍返回 AxiosResponse, 所以拦截器里对 response.data
 * 做原地替换（保留 response 外壳以满足类型系统）, 再由下面各方法做一次性收窄。
 * 这一步把「无法静态校验的信封拆解」收敛到唯一位置, 避免类型断言散落各处。
 */
function unwrap<T>(promise: Promise<{ data: unknown }>): Promise<T> {
  return promise.then((res) => res.data as T)
}

instance.interceptors.response.use(
  (response) => {
    const res = response.data as ApiResult<unknown>
    if (res.code === 0) {
      // 原地拆掉 Result 包装, 业务数据放回 response.data
      response.data = res.data
      return response
    }
    if (res.code === 40100) {
      handleUnauthorized()
    }
    throw new BizError(res.code, res.message)
  },
  (error) => {
    // 网络层错误（超时/断网/5xx）: 统一收敛为 50000。
    // 原始错误保留到控制台与 BizError.cause, 便于排障时不丢上下文
    if (import.meta.env.DEV) {
      console.error('[xinyu] 网络请求失败:', error)
    }
    throw new BizError(50000, '网络开小差了，请稍后重试', error)
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

/**
 * 类型化请求方法: 返回值即为 Result.data
 *
 * 类型参数 T 需与后端契约一致（契约定义见 types/api.ts, 与后端 schemas 人工对齐）。
 * 拦截器已保证 code === 0 才会走到这里, 因此 res.data 就是 T。
 */
export function get<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
  return unwrap<T>(instance.get(url, config))
}

export function post<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
  return unwrap<T>(instance.post(url, data, config))
}

export function put<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
  return unwrap<T>(instance.put(url, data, config))
}

export function del<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
  return unwrap<T>(instance.delete(url, config))
}
