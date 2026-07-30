import { get, post } from '@/api/request'
import type { AuthResponse, LoginDTO, RegisterDTO, UserVO } from '@/types/api'

/**
 * 认证接口封装（契约一 1.3）
 * 页面/store 只依赖本模块, 不直接触碰 axios
 */
export const authApi = {
  /** 注册（注册即登录, 直接拿 token） */
  register(dto: RegisterDTO): Promise<AuthResponse> {
    return post('/auth/register', dto)
  },

  /** 登录 */
  login(dto: LoginDTO): Promise<AuthResponse> {
    return post('/auth/login', dto)
  },

  /** 当前用户信息（刷新页面后校准缓存, 兼校验 token 是否有效） */
  fetchMe(): Promise<UserVO> {
    return get('/users/me')
  },
}
