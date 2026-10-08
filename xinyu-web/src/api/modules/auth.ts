import { get, post, put } from '@/api/request'
import type { AuthResponse, LoginDTO, RegisterDTO, UpdatePasswordDTO, UserVO } from '@/types/api'

/**
 * 认证/账户接口封装（契约一 1.3）
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

  /** 修改密码（原密码错误返回 42200 "原密码错误"; 成功后旧 token 仍有效, 无需重新登录） */
  updatePassword(dto: UpdatePasswordDTO): Promise<void> {
    return put('/users/me/password', dto)
  },

  /** 绑定/更换邮箱 (传 null 清除) */
  updateEmail(email: string | null): Promise<void> {
    return put('/users/me/email', { email })
  },

  /** 上传头像 (M5): JPG/PNG/WEBP ≤2MB, 返回更新后的用户信息 */
  updateAvatar(file: File): Promise<UserVO> {
    const fd = new FormData()
    fd.append('file', file)
    return put('/users/me/avatar', fd)
  },
}
