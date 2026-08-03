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
  /** 会话级模型覆盖, null=用用户默认模型 */
  modelId: string | null
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
  /** 输入 token（仅 ASSISTANT） */
  promptTokens: number | null
  /** 输出 token（仅 ASSISTANT） */
  completionTokens: number | null
  /** 实际使用模型（仅 ASSISTANT） */
  modelCode: string | null
  createdAt: string
}

// ---------- SSE 事件载荷（契约二 2.2, 与后端 Sse*VO 逐字段一致） ----------

/** event:meta — 双消息 ID 回执 */
export interface SseMetaEvent {
  userMessageId: string
  assistantMessageId: string
}

/** event:delta — 增量文本 */
export interface SseDeltaEvent {
  content: string
}

/** event:done — 终态回执 */
export interface SseDoneEvent {
  messageId: string
  /** 输入 token */
  promptTokens: number
  /** 输出 token */
  completionTokens: number
  status: MessageStatus
}

/** event:error — LLM 异常（51001~51004） */
export interface SseErrorEvent {
  code: number
  message: string
}

// ---------- 用量统计（M2 Token 统计） ----------

/** GET /api/stats/usage 返回体 */
export interface UsageStatsVO {
  /** 统计日期（今日那一档对应的日期, YYYY-MM-DD） */
  date: string
  todayCallCount: number
  todayPromptTokens: number
  todayCompletionTokens: number
  /** 估算成本（元） */
  todayCost: number
  totalCallCount: number
  totalPromptTokens: number
  totalCompletionTokens: number
  totalCost: number
}

// ---------- 模型管理（M2 多模型切换） ----------

/** GET /api/models 返回项 / POST、PUT 返回体 */
export interface AiModelVO {
  id: string
  /** 供应商: QWEN/OPENAI/DEEPSEEK/MOONSHOT/GLM/OLLAMA... */
  provider: string
  /** 模型标识: qwen-plus / gpt-4o / deepseek-chat */
  modelCode: string
  /** 用户自定义展示名 */
  displayName: string
  /** OpenAI 兼容接口地址 */
  baseUrl: string
  /** API Key 掩码: 已配置返回 "已配置 ****", 未配置返回 null */
  apiKeyMasked: string | null
  /** 是否默认模型（1=是） */
  isDefault: number
  /** 是否启用（1=是） */
  enabled: number
  createdAt: string
}

/** 添加/编辑模型请求体 */
export interface AiModelSaveDTO {
  provider: string
  modelCode: string
  displayName: string
  baseUrl: string
  /** 明文 API Key: 添加必填, 编辑可空（空则保留原值） */
  apiKey?: string
  isDefault?: boolean
}

// ---------- 长期记忆（M2.1 Memory） ----------

/** GET /api/memories 返回项 */
export interface MemoryVO {
  id: string
  userId: string
  characterId: string
  /** 角色名（冗余, 便于展示） */
  characterName: string
  /** 结构化键: name/job/location/hobby/preference/personality/relationship/goal/fact, 可空 */
  memoryKey: string | null
  content: string
  /** HIGH/MEDIUM/LOW */
  importance: 'HIGH' | 'MEDIUM' | 'LOW'
  /** ACTIVE/DISABLED */
  status: 'ACTIVE' | 'DISABLED'
  /** 提取来源会话（可溯源） */
  sourceConversationId: string | null
  createdAt: string
  updatedAt: string
}

/** PUT /api/memories/{id} 请求体 */
export interface MemoryUpdateDTO {
  content: string
  importance: 'HIGH' | 'MEDIUM' | 'LOW'
  status: 'ACTIVE' | 'DISABLED'
}

// ---------- 角色管理（M2.2 角色 CRUD + Prompt 编辑器） ----------

/** 角色状态 */
export type CharacterStatus = 'DRAFT' | 'PENDING' | 'PUBLISHED' | 'OFFLINE'

/** GET /api/characters 返回项 / POST、PUT 返回体 */
export interface CharacterVO {
  id: string
  name: string
  avatarUrl: string | null
  intro: string | null
  systemPrompt: string
  greeting: string
  temperature: number
  maxTokens: number
  modelId: string | null
  creatorId: string
  /** OFFICIAL / USER */
  creatorType: 'OFFICIAL' | 'USER'
  status: CharacterStatus
  chatCount: number
  favoriteCount: number
  createdAt: string
  updatedAt: string
  /** 是否当前用户创建（前端判断可否编辑/删除） */
  mine: boolean

  /** 是否已被当前用户收藏（广场/详情页用, 游客恒为 false） */
  favorited: boolean
}

/** 添加/编辑角色请求体 */
export interface CharacterSaveDTO {
  name: string
  avatarUrl?: string | null
  intro?: string
  systemPrompt: string
  greeting: string
  temperature: number
  maxTokens: number
  /** 创建可不传(默认 DRAFT); 编辑可传 DRAFT/PUBLISHED/OFFLINE */
  status?: CharacterStatus
}

