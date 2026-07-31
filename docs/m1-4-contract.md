# M1-4 前端聊天 MVP · 三份契约（v1.0 已冻结）

> 本文档是 M1-4 编码前的契约定稿，全部字段以 **后端已实现代码** 为准
> （Result.java / ResultCode.java / ChatController / AuthController / UserController / ChatServiceImpl），
> 不是设计设想稿。**GPT 评审已通过，4 项调整已合入，本版本为编码期间唯一对齐依据，不再变更。**

---

## 契约一：前后端 API 契约

### 1.1 全局约定

| 项 | 约定 |
|---|---|
| Base URL | 开发环境前端 `/api/**` 由 Vite 代理到 `http://localhost:9000`（已配置） |
| 响应包装 | 所有 JSON 接口统一 `{ code, message, data }`；HTTP 状态码恒为 200，业务状态看 `code`（SSE 接口除外） |
| 成功 | `code === 0` |
| 认证 | `Authorization: Bearer <token>`；token 有效期 2h，无 RefreshToken，过期即重新登录 |
| ID 类型 | **所有雪花 ID 后端序列化为字符串**（防 JS 精度丢失），前端 TS 类型一律 `string`，禁止 `Number()` 转换 |
| 时间 | `LocalDateTime` 序列化为 `"2026-07-30T12:46:42"`（无时区后缀），前端展示时按本地时间解析 |

### 1.2 错误码表（前端统一处理层的分支依据）

| code | 含义 | 前端行为 |
|---|---|---|
| 0 | 成功 | 取 `data` |
| 40100 | 未登录 / token 过期 / 登录失败 | **清 token → 跳登录页**（登录页内的 40100 只展示 message，不跳转） |
| 40300 | 无权限 | Toast 提示 |
| 40400 | 资源不存在（含越权访问他人会话） | Toast / 空状态页 |
| 42200 | 参数或业务校验失败（含用户名已存在、重复消息） | 表单内联提示或 Toast，展示 `message` |
| 50000 | 系统异常 | Toast「系统开小差了」 |
| 51001~51004 | LLM 异常（仅出现在 SSE error 事件） | 聊天气泡内角色化文案 + 重试按钮 |

### 1.3 接口清单（后端已实现，共 7 个）

**认证（白名单，匿名可访问）**

```
POST /api/auth/register
  body: { username: string, password: string, nickname?: string }
  校验: username ^[a-zA-Z0-9_]{4,16}$ ; password 6-20位 ; nickname ≤30字
  data: { token: string, user: UserVO }        # 注册即登录
  失败: 42200 用户名已存在 / 42200 参数校验

POST /api/auth/login
  body: { username: string, password: string }
  data: { token: string, user: UserVO }
  失败: 40100 「用户名或密码错误」（用户不存在与密码错误统一文案）
```

**用户（需登录）**

```
GET /api/users/me
  data: UserVO

PUT /api/users/me/password
  body: { oldPassword: string, newPassword: string }   # newPassword 6-20位
  data: null
  失败: 40100 原密码错误
```

**会话/消息（需登录）**

```
GET /api/conversations                                 # M1-4.1 已补齐
  data: ConversationVO[]                               # 仅本人会话, 按 last_message_at 倒序

POST /api/conversations
  body: { characterId: string, title?: string }        # title 缺省=角色名
  data: ConversationVO
  失败: 40400 角色不存在

GET /api/conversations/{id}/messages?before=&size=
  游标分页: before=游标消息ID(缺省最新一页), size 缺省20 上限100
  data: MessageVO[]                                    # 升序（旧→新），前端直接顺序渲染
  加载更早: before = 当前列表第一条的 id；返回条数 < size 说明到头
  失败: 40400 会话不存在（含他人会话）

POST /api/conversations/{id}/chat                      # SSE，见契约二
```

### 1.4 实体类型（前端 `src/types/api.d.ts` 依此声明）

```ts
interface UserVO {
  id: string
  username: string
  nickname: string
  avatarUrl: string | null
}

interface ConversationVO {
  id: string
  characterId: string
  title: string
  lastMessageAt: string | null
  lastMessagePreview: string | null
  createdAt: string
}

type MessageRole = 'USER' | 'ASSISTANT' | 'SYSTEM'
type MessageStatus = 'GENERATING' | 'COMPLETED' | 'FAILED' | 'STOPPED'

interface MessageVO {
  id: string
  conversationId: string
  sequenceNo: number
  messageType: MessageRole
  content: string
  status: MessageStatus
  completionTokens: number | null
  createdAt: string
}
```

### 1.5 接口缺口处理结果（评审定稿）

- `GET /api/conversations`：**M1-4.1 已补齐**（ConversationController，单独 commit），支撑「刷新页面消息还在」验收项。
- `PUT /api/conversations/{id}`（重命名）、`DELETE /api/conversations/{id}`：**延后**，当前 MVP 不需要，避免范围膨胀。

---

## 契约二：SSE 事件契约

### 2.1 请求

```
POST /api/conversations/{id}/chat
Headers: Authorization: Bearer <token>
         Content-Type: application/json
Body:    { content: string, clientMessageId?: string }
         # content ≤2000字; clientMessageId = 前端生成 UUID, 幂等防重
响应:    Content-Type: text/event-stream（SseEmitter 自动设置）
超时:    服务端 180s；业务异常(40400/42200/40100)时返回普通 JSON 而非事件流
```

原生 `EventSource` 不支持 POST + 自定义 Header，前端固定使用
**`@microsoft/fetch-event-source`**（M1-4 新增依赖）。

### 2.2 事件序列（wire format，与后端 ChatServiceImpl 逐字段一致）

正常流：`meta → delta × N → done`；失败流：`meta → [delta × n] → error`。

```
event:meta
data:{"userMessageId":"2082689315607863297","assistantMessageId":"2082689315607863298"}

event:delta
data:{"content":"你好呀，"}

event:done
data:{"messageId":"2082689315607863298","completionTokens":52,"status":"COMPLETED"}

event:error
data:{"code":51001,"message":"AI 服务连接失败"}
```

字段说明：
- `meta`：双消息 ID 回执（均为字符串）。前端收到后：把本地乐观插入的 USER 消息换成真实 ID；插入 ASSISTANT 占位气泡（status=GENERATING，「●●●呼吸」）。
- `delta.content`：增量文本，**追加**到占位气泡；首个 delta 到达时 UI 从"思考中"切换为打字机。
- `done`：终态回执。前端把气泡 status 置 COMPLETED、记录 completionTokens、渲染 Markdown + 操作条。
- `error`：气泡置 FAILED，按 code 显示角色化文案 + 重试按钮。`error` 后连接由服务端 complete，前端无需手动关闭。

### 2.3 前端连接生命周期（useSseChat composable 的职责边界）

| 场景 | 前端动作 | 后端行为（已实现，前端可信赖） |
|---|---|---|
| 发送 | `fetchEventSource(url, { signal: AbortController.signal })` | 落库 USER + GENERATING 占位 |
| 停止生成 | `abortController.abort()`，本地气泡置 STOPPED | 检测断连 → 消息置 STOPPED，**保留已生成文本** |
| 断网/异常断开 | onerror 回调：气泡置 FAILED（本地视角），**必须 `throw` 阻止 fetch-event-source 默认自动重试**（重试会重复落库 USER 消息） |
| 生成中切换会话/离开页面 | 同「停止生成」，abort 旧连接 | 同上，置 STOPPED |
| HTTP 非 200 或返回 JSON（如 40100） | onopen 里检查 Content-Type，非 event-stream 按普通错误处理（40100 走全局跳登录） | 业务异常直接 JSON 返回 |
| 生成中禁止并发发送 | 发送按钮变「停止」，`sending` 状态锁 | —（服务端不做并发锁，M1-4 由前端保证单会话串行） |

其它要点：
- `openWhenHidden: true`（后台标签页不断开，避免切 tab 触发意外重连）。
- 打字机渲染节流：delta 先入缓冲，**每 50ms 批量 flush 到响应式状态**（design.md 4.4 已定）。
- 刷新页面时若上一条 ASSISTANT 仍是 GENERATING（极端时序）：按 STOPPED 样式展示，不恢复连接。

---

## 契约三：Pinia 状态流转

### 3.1 Store 拆分（M1-4 只建三个，禁止万能 store）

```
stores/
├── auth.ts           # token + 当前用户
├── conversation.ts   # 会话列表 + 当前会话
└── message.ts        # 当前会话的消息 + 流式状态
```

**auth store**

```ts
state:   { token: string | null, user: UserVO | null }
getters: { isLoggedIn: token 非空 }
actions: {
  register(dto) / login(dto)   # 成功后写 token+user 并持久化
  fetchMe()                    # 刷新页面后校验 token 兼拉取用户
  logout()                     # 清 state + localStorage → 跳登录（退出=前端删token，无后端接口）
}
持久化: token 与 user 存 localStorage（key: xinyu_token / xinyu_user），
        main.ts 初始化时回填; 2h 过期由 40100 统一兜底处理
```

**conversation store**

```ts
state:   { list: ConversationVO[], activeId: string | null, loading: boolean }
getters: { active }
actions: {
  fetchList()                        # GET /api/conversations（依赖 1.5 补口）
  create(characterId, title?)        # 创建后 unshift 进列表并设为 active
  setActive(id)                      # 切换会话（触发 message.load + abort 旧SSE）
  applyPreview(id, preview, at)      # SSE done 后本地刷新列表冗余字段, 不重拉接口
}
```

**message store**（SSE 状态唯一落点，连接本身由 useSseChat 管理）

```ts
state: {
  items: MessageVO[]          # 当前会话消息, 升序
  hasMore: boolean            # 游标分页是否还有更早消息
  loadingHistory: boolean
  streaming: boolean          # 是否有进行中的生成（发送按钮⇄停止按钮）
}
actions: {
  load(conversationId)                    # 最新一页
  loadEarlier()                           # before=items[0].id, 结果 prepend
  # ↓ 以下由 useSseChat 在事件回调中调用（store 只存状态，不管连接）
  appendUserMessage(tempMsg)              # 发送瞬间乐观插入(临时id)
  confirmMeta(userMessageId, assistantMessageId)   # 换真实id + 插GENERATING占位
  appendDelta(text)                       # 50ms 节流批量追加
  finishAssistant(done)                   # → COMPLETED + tokens
  failAssistant(code, message)            # → FAILED
  stopAssistant()                         # → STOPPED（本地abort时）
}
```

### 3.2 一次发送的完整状态流转（核心时序，联调对照用）

```
用户点发送
  ├─ message.appendUserMessage(临时USER消息)   → 气泡立即上屏
  ├─ message.streaming = true                  → 按钮变「停止」
  └─ useSseChat.send(content, clientMessageId=uuid)
        │
        ├─ event:meta  → confirmMeta()         → USER换真实id + AI占位「●●●」(GENERATING)
        ├─ event:delta → appendDelta()         → 首个delta: 占位切打字机; 后续追加
        ├─ event:done  → finishAssistant()     → COMPLETED, 渲Markdown
        │                conversation.applyPreview()  → 列表摘要/排序刷新
        │                streaming = false     → 按钮复原
        └─ event:error → failAssistant()       → FAILED + 角色化文案 + 重试
                         streaming = false
用户点停止: abort() → stopAssistant() → STOPPED(保留已出文本), streaming=false
```

MessageStatus → UI 映射（MessageStatus.vue 组件，气泡内不堆判断）：

| status | UI |
|---|---|
| GENERATING | 无文本=「●●●呼吸」；有文本=打字机+光标 |
| COMPLETED | Markdown 渲染 + hover 操作条 |
| FAILED | 角色化错误文案 + 重试按钮 |
| STOPPED | 已生成文本 + 「已停止生成」灰字标注 |

### 3.3 路由与守卫（M1-4 范围）

```
/login        登录/注册同页Tab（左品牌视觉+右毛玻璃表单, M1-4 允许简化视觉）
/chat         三栏聊天主界面（requiresAuth）
/             重定向 → 已登录 ? /chat : /login    # M2 有广场后游客落地改 /square
```

守卫：`requiresAuth` 且无 token → 跳 `/login?redirect=原路径`；axios 响应拦截统一处理 40100。

### 3.4 组件拆分与文件清单（编码阶段照此建文件）

```
src/
├── api/                 # axios 实例(拦截器) + auth.ts / conversation.ts 接口封装
├── composables/useSseChat.ts
├── stores/{auth,conversation,message}.ts
├── types/api.d.ts
├── views/{LoginView,ChatView}.vue        # ChatView <150行, 纯编排
└── components/
    ├── base/            # 本期仅需: XyButton XyInput XyToast XyAvatar XyAiLoading
    └── chat/            # ConversationList ChatHeader MessageList ChatInput
                         # MessageBubble → MarkdownRenderer/MessageStatus/TypingIndicator
```

约束（design.md 已定）：单 Vue 文件 <300 行；SSE 连接只存在于 useSseChat；store 只存状态。

前端工程约束（评审补充）：
- **统一错误处理**：axios 响应拦截器里 `res.code !== 0` 统一 `throw BizError(code, message)`，页面层只写成功分支，禁止各页面自判 code。
- **token 存取封装**：`utils/storage.ts` 导出 `tokenStorage { get/set/remove }`，禁止任何页面/store 直接调 `localStorage`（将来换 sessionStorage/httpOnly cookie 只改一处）。

新增依赖：`@microsoft/fetch-event-source`、`marked` + `dompurify` + `highlight.js`。
Markdown 渲染链路固定：`marked.parse()` → `DOMPurify.sanitize()` → `v-html`，**禁止未消毒直接输出**。

---

## 决策定稿（GPT 评审通过，契约随之冻结 v1.0）

| # | 决策 | 定稿结果 |
|---|---|---|
| D1 | 种子角色 | `scripts/seed-dev.sql`（与 schema.sql 职责分离）：creator_id 不硬编码，用 `@admin_username` 变量查出；官方角色「屿屿」固定 id=1001，前端 M1-4 写死引用 |
| D2 | 会话列表接口 | 只补 `GET /api/conversations`（M1-4.1 已完成，单独 commit）；PUT/DELETE 延后，避免范围膨胀 |
| D3 | token 存储 | localStorage + `tokenStorage` 封装（禁止裸调）；2h 过期由 40100 统一兜底 |
| D4 | Markdown | marked + DOMPurify + highlight.js，消毒后才 v-html |

## M1-4 编码顺序（评审定稿，4 个 commit）

1. `feat(chat): add conversation list api` —— 后端 GET /api/conversations ✅（已完成）
2. `feat(web): setup api client and auth flow` —— axios / tokenStorage / 路由守卫 / LoginView
3. `feat(web): implement chat interface` —— ChatView + ConversationList/MessageList/ChatInput（静态接数据，不接 SSE）
4. `feat(web): integrate sse streaming chat` —— fetch-event-source / message store 流式状态 / delta 节流渲染 / stop / error
