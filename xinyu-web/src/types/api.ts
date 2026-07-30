/**
 * 后端接口类型声明（与 docs/m1-4-contract.md 契约一 1.4 对齐）
 *
 * 注意: 所有雪花 ID 均为字符串, 禁止 Number()/parseInt() 转换
 * （超过 Number.MAX_SAFE_INTEGER 会丢精度）
 *
 */

/** 后端统一响应包装: HTTP 恒 200, 业务状态看 code, 0 为成功 */
export interface ApiResult<T> {
  code: number
  message: string
  data: T
}

export interface UserVO {
  id: string
  username: string
  nickname: string
  avatarUrl: string | null
}

/** 注册/登录共用响应: 注册即登录 */
export interface AuthResponse {
  token: string
  user: UserVO
}

export interface RegisterDTO {
  username: string
  password: string
  nickname?: string
}

export interface LoginDTO {
  username: string
  password: string
}

export interface ConversationVO {
  id: string
  characterId: string
  title: string
  lastMessageAt: string | null
  lastMessagePreview: string | null
  createdAt: string
}

export interface CreateConversationDTO {
  characterId: string
  /** 缺省 = 角色名 */
  title?: string
}

export type MessageRole = 'USER' | 'ASSISTANT' | 'SYSTEM'
export type MessageStatus = 'GENERATING' | 'COMPLETED' | 'FAILED' | 'STOPPED'

export interface MessageVO {
  id: string
  conversationId: string
  /** 会话内业务序号 */
  sequenceNo: number
  messageType: MessageRole
  /** Markdown 原文（M1-4.3 先按纯文本展示, 渲染链路 M1-4.4 接入） */
  content: string
  status: MessageStatus
  /** 输出 token（仅 ASSISTANT） */
  completionTokens: number | null
  createdAt: string
}
