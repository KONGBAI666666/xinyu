# 心屿 XinYu · 技术设计文档

> 版本：v1.0（设计阶段定稿）
> 本文档汇总五轮设计评审（模块划分 → 数据库 → API → 页面结构 → 项目目录）的最终结论，
> 是 M1~M5 全部开发阶段的唯一设计依据。设计变更须先更新本文档再改代码。

---

## 0. 项目定位

- **名称**：心屿（XinYu）
- **一句话**：原创 AI 角色聊天平台，支持 AI 角色、多轮上下文、长期记忆、SSE 流式回复和沉浸式聊天体验。
- **性质**：可部署上线、可放 GitHub 的校招简历主打作品集项目，不是课程设计 Demo。
- **原创性**：不复制或模仿任何现有产品的代码、UI、Logo、品牌、文案、素材。
- **风格关键词**：温暖 · 陪伴 · 未来感 · 沉浸式 · 高级 · 简约。
- **明确不做**：Redis、RabbitMQ、Elasticsearch、微服务、向量数据库（除非确有需要）。

### 里程碑路线

| 里程碑 | 内容 | 周期 |
|---|---|---|
| M1 可聊天地基 | 登录注册(JWT)、会话列表、SSE 流式 + 思考状态、Markdown、聊天记录、消息操作(复制/删除/重新生成)、深色主题、内置默认角色 | 1~2 周 |
| M2 角色平台 | 角色广场(搜索/标签/收藏)、角色详情、Prompt 可视化编辑器、消息赞踩 | 1 周 |
| M3 记忆与统计 | AI 自动提取长期记忆、记忆注入、记忆管理页、聊天/Token 统计 | 1~2 周 |
| M4 管理后台 | 用户管理、角色审核、标签管理、数据看板、差评消息列表 | 1 周 |
| M5 打磨上线 | 响应式、动画、README、架构图、部署 | 3~5 天 |

---

## 1. 模块划分

### 1.1 总体架构

前端 Vue3 SPA（C 端沉浸式聊天 + B 端 Element Plus 后台）
→ HTTP(JSON) + SSE
→ 后端 Spring Boot 3 **模块化单体**
→ MyBatis-Plus → MySQL。

**为什么单体不是微服务**：单人开发 + 演示部署场景，模块化单体是正确选择；
模块边界靠 Java 包结构约束，将来可拆。"评估过微服务并决定不用"本身是架构判断力的体现。

### 1.2 后端模块

| 模块 | 职责 | 里程碑 |
|---|---|---|
| auth | 注册、登录、JWT 签发（简化方案：仅 access_token 2h，退出=前端删 token） | M1 |
| user | 个人信息、头像、改密码 | M1 |
| character | 角色 CRUD、广场、收藏、状态机(DRAFT→PENDING→PUBLISHED→OFFLINE) | M1 建表 / M2 完整 |
| conversation | 会话 CRUD，会话=用户×角色的一次对话线 | M1 |
| message | 消息 CRUD、反馈、游标分页 | M1 |
| chat | 编排模块（无表）：SSE 推送、上下文组装(ContextAssembler)、重新生成 | M1 |
| llm | LlmClient 接口抽象 + QwenLlmClient 实现（OpenAI 兼容协议） | M1 |
| memory | 异步提取记忆、记忆 CRUD、注入供给 | M3 |
| stats | 聚合统计（无表） | M3 |
| admin | 管理视角 Controller，复用业务 Service；RBAC 简化为 USER/ADMIN | M4 |
| common | 统一返回、异常、JWT 过滤器、参数校验、日志、配置 | M1 |
| file | 仅预留表，不建包不做接口 | 预留 |

**依赖方向（硬规则）**：`admin → 业务模块 → common`；`chat → message/character/memory/llm`。
禁止业务模块互相乱引用；出现共同逻辑提取到 common 或新增独立模块，不允许横向调用形成循环依赖。

### 1.3 聊天核心数据流

```
用户发送 → chat 落库(USER消息)
→ ContextAssembler：角色 System Prompt + 记忆(M3) + 最近N轮历史 + 当前输入
→ llm 流式调用 → SSE 逐段推前端（思考中→打字中）
→ 完整回复落库(含 token 用量) → [M3] 异步触发记忆提取
```

---

## 2. 数据库设计

### 2.1 全局约定

| 约定 | 方案 | 理由 |
|---|---|---|
| 库/字符集 | `xinyu` / utf8mb4 + utf8mb4_unicode_ci | 聊天必有 emoji |
| 主键 | BIGINT 雪花 ID（MyBatis-Plus ASSIGN_ID） | URL 中不暴露业务量、不可遍历 |
| 逻辑删除 | 业务表统一 `deleted` + @TableLogic | 聊天记录是核心资产 |
| 时间 | `created_at` / `updated_at` 自动填充 | 统一审计 |
| 外键 | 不建物理外键，应用层保证 + 索引 | 企业惯例，避免锁开销 |
| 枚举 | VARCHAR(20) 存枚举名 | 可读性优先 |

### 2.2 表清单（10 张，M1 一次建齐）

user / character / tag / character_tag / character_favorite / conversation / message / memory / ai_model / file(预留)

### 2.3 核心表要点（完整 DDL 见 scripts/schema.sql）

**user**：uk_username；username 4-16 位字母数字下划线（后端校验）；password BCrypt；
role=USER/ADMIN；status=ACTIVE/BANNED。

**character**：intro(广场卡片) + system_prompt(TEXT) + greeting；temperature DECIMAL(3,2) 默认 0.80；
max_tokens 默认 1024；model_id 可空(空=系统默认模型)；creator_type=OFFICIAL/USER；
status 四态状态机；chat_count/favorite_count 冗余计数（广场排序防 N+1）。
索引：idx_square(status, creator_type)、idx_creator(creator_id)。

**conversation**：last_message_at + last_message_preview 双冗余（会话列表免 join）。
索引：idx_user_last(user_id, last_message_at)。

**message（核心表）**：
- message_type = USER / ASSISTANT / SYSTEM
- status 状态机 = GENERATING / COMPLETED / FAILED / STOPPED（SSE 中断容错：先落 GENERATING 占位，结束时更新）
- parent_message_id：消息树，重新生成=同 parent 下挂新 ASSISTANT 候选，旧的逻辑删除
- sequence_no：会话内自增业务序号（MAX+1，单用户写无并发问题），上下文按它排序
- client_message_id：前端 UUID，幂等防重，uk(user_id, client_message_id)（可空，NULL 不冲突）
- prompt_tokens / completion_tokens / model_code：token 统计与模型溯源
- feedback = NONE / LIKE / DISLIKE；regenerate_count
- user_id 冗余（统计/权限免 join）
- 索引：idx_conv(conversation_id, id)、idx_user_created(user_id, created_at)、idx_feedback(feedback)

**memory**：memory_key(结构化键，如 hobby/job/style) + content + importance(HIGH/MEDIUM/LOW)
+ status(ACTIVE/DISABLED) + source_conversation_id(可溯源)。
索引：idx_inject(user_id, character_id, status, importance) 注入查询全覆盖。

**ai_model**：provider/model_code/base_url/is_default/enabled；**API Key 不入库**，存配置文件/环境变量。

### 2.4 演进备查（暂不做）

message 按会话分表；daily_stat 汇总表；system_prompt 版本表。

---

## 3. API 设计

### 3.1 全局约定

- 统一返回：`{ "code": 0, "message": "ok", "data": {} }`；成功 code=0。
- 错误码分段：40100 未登录 / 40300 无权限 / 40400 不存在 / 42200 参数校验 / 50000 系统异常；
  LLM 细分：51001 连接失败 / 51002 超时 / 51003 Token 超限 / 51004 内容安全拦截（前端做角色化文案）。
- 认证：`Authorization: Bearer <token>`；白名单：登录/注册/角色广场（游客可逛）/health。
- 分页：普通列表 page+size 返回 `{records,total}`；**消息历史用游标分页** `?before=<messageId>&size=20`。
- 校验：Bean Validation 注解在 DTO 上；username 4-16 位字母数字下划线，password 6-20 位。
- `GET /api/health` → `{status:"UP"}`（部署探活）。

### 3.2 接口清单（30 个）

**认证/用户（M1）**
- POST /api/auth/register（注册即登录，返回 token）
- POST /api/auth/login → { token, user }
- GET /api/users/me · PUT /api/users/me · PUT /api/users/me/password

**角色/标签（M2，建表 M1）**
- GET /api/characters?keyword=&tagId=&sort=recommend|hot|new&page=&size=（游客可访问；recommend M2 先等价 hot，预留综合排序）
- GET /api/characters/{id} · POST /api/characters（初始 DRAFT）· PUT /api/characters/{id}（PUBLISHED 编辑后回 PENDING）
- POST /api/characters/{id}/submit（DRAFT→PENDING，状态流转用显式动作接口而非 PUT status）
- DELETE /api/characters/{id} · GET /api/characters/mine
- POST|DELETE /api/characters/{id}/favorite · GET /api/characters/favorites
- GET /api/tags

**会话/消息（M1）**
- POST /api/conversations  body:{ characterId, title? }（title 可选，缺省自动生成；创建时写入 greeting 为首条 ASSISTANT 消息）
- GET /api/conversations（按 last_message_at 倒序，带冗余预览）
- PUT /api/conversations/{id}（重命名）· DELETE /api/conversations/{id}
- GET /api/conversations/{id}/messages?before=&size=20
- DELETE /api/messages/{id} · PUT /api/messages/{id}/feedback  body:{ feedback }

**SSE 聊天（M1 核心）**
- POST /api/conversations/{id}/chat  body:{ content, clientMessageId }  → text/event-stream
- POST /api/messages/{id}/regenerate（{id} 必须是 ASSISTANT 消息，否则 42200/40000）→ text/event-stream

事件协议：
```
event: meta   → { userMessageId, assistantMessageId }   前端立即渲染占位（AI 侧"思考中"）
event: delta  → { content }                              增量文本，打字机渲染
event: done   → { messageId, completionTokens, status }
event: error  → { code, message }                        消息置 FAILED，前端显示重试
```
- 前端用 @microsoft/fetch-event-source（POST + Authorization 头，原生 EventSource 不支持）。
- **上下文完全服务端组装**，前端只传当前输入（防篡改/省流量/职责归位）。
- 停止生成：前端 AbortController 断连，服务端回调将消息置 STOPPED 并保留已生成内容，不做独立 stop 接口。

**记忆/统计（M3）**
- GET /api/memories?characterId= · PUT /api/memories/{id} · DELETE /api/memories/{id}（提取为服务端异步内部行为，不暴露接口）
- GET /api/stats/me → UserStatsVO{ todayChatCount, totalTokens, favoriteCount, recentConversations }（DTO 输出，不返回 Map）

**管理后台（M4，/api/admin/**，role=ADMIN）**
- GET /api/admin/users · PUT /api/admin/users/{id}/status
- GET /api/admin/characters?status=PENDING · POST /api/admin/characters/{id}/review  body:{ action:APPROVE|REJECT, reason }
- GET|POST|PUT|DELETE /api/admin/tags
- GET /api/admin/stats/overview · GET /api/admin/messages/disliked

### 3.3 已知取舍（备查）

token 无法主动吊销（封禁最长 2h 生效）；SSE 不做断点续传；接口限流 M5 用内存限流器。

---

## 4. 页面结构

### 4.1 Design Tokens（深色主题·默认，全部落地 tokens.css CSS 变量）

| Token | 值 |
|---|---|
| 背景基底 | #0C1020 深海夜蓝 |
| 浮层 | rgba(255,255,255,0.06) + backdrop-blur(20px) 毛玻璃 |
| 品牌渐变 | #8B7CF6 暮紫 → #5EEAD4 屿青 |
| 暖色点缀 | #FFB86B（收藏/情感元素专用） |
| 文字三级 | #EAECF5 / #9AA1B5 / #5C6379 |
| 圆角 | 卡片 16 / 气泡 18(发送方向角 4) / 按钮 12 |
| 动效 | 180ms ease-out；消息入场 fade+8px 上移；AI 思考三点呼吸 |

- 主题机制：html.dark/.light 切换整套变量；M1 只做深色，浅色 M5 补。
- **硬规定**：颜色只允许引用 tokens.css 变量；Markdown 渲染 markdown-it + highlight.js，输出必须过 DOMPurify（XSS 防线）。

### 4.2 路由

```
C 端（MainLayout：左侧 64px 图标导航 + 内容区）
/login  /square(游客可)  /characters/:id(游客可)  /chat  /chat/:conversationId
/studio  /studio/create  /studio/:id/edit  /me  /me/memories
B 端（AdminLayout：Element Plus，与 C 端完全隔离）
/admin  /admin/users  /admin/characters  /admin/tags  /admin/feedback
/:pathMatch(.*)  404
```
守卫：requiresAuth → 跳登录带回跳；requiresAdmin → 非管理员返回 404（不暴露后台存在）。游客默认落地 /square。

### 4.3 页面要点

- **登录页**：左品牌视觉（渐变背景 + Logo 动效 + 轻量 CSS 漂浮粒子；M1 不做 WebGL/Canvas）右毛玻璃表单，登录/注册同页 Tab。
- **聊天主界面**：三栏（导航 64px / 会话列表 280px / 聊天区）。
  AI 状态机 UI：发送 → 气泡"●●●呼吸" → 首个 delta 切打字机 → done 渲染 Markdown + 操作条。
  生成中发送按钮变停止；FAILED 显示角色化文案 + 重试；消息操作 hover 出现；
  聊天背景叠加角色主题色氛围光（M1 固定色，M5 头像取色）；
  **空状态**：未选会话显示欢迎引导（"欢迎来到心屿 / 选择一个角色，开始你的第一次对话 / [探索角色]"）。
  响应式：<1024 列表可折叠；<768 三栏变栈式导航三页。
- **角色广场**：搜索 + 标签横滑胶囊 + 排序（推荐/热门/最新）；网格卡片 hover 浮起出现"开始聊天"；游客点击引导登录。
- **角色详情**：模糊头像背景 + 开场白气泡预览 + 悬浮双按钮（开始聊天/收藏）。
- **Prompt 编辑器**：左表单（基础信息/人设/参数滑块，Temperature 带"严谨↔发散"语义文案）右**实时预览**（广场卡片 + 开场白气泡）；底部存草稿/提交审核。
- **个人中心**：统计卡片（数字滚动）+ 收藏/我的角色/设置 Tab。
- **记忆管理**：角色筛选胶囊 + 记忆卡片（重要度标识/来源链接/启用开关）；空状态文案"TA 还在慢慢了解你"。
- **管理后台**：看板（4 数字卡 + ECharts 7 日趋势，ECharts 仅 admin 路由懒加载）；审核抽屉（看完整 prompt，驳回必填理由）；差评列表带上下文。

### 4.4 组件与状态

- 公共组件 base/：XyButton XyInput XyModal XyAvatar XyTag XyToast XySlider XyEmpty **XyAiLoading**（三渐变点呼吸，用于初始化/SSE 连接等待）。
- 聊天页拆分：ChatView(<150 行编排) → ConversationList/ChatHeader/MessageList/ChatInput；
  MessageBubble → MarkdownRenderer / **MessageStatus**（对应 GENERATING/COMPLETED/FAILED/STOPPED，避免气泡内堆判断）/ MessageActions / TypingIndicator。
- 单 Vue 文件 <300 行（硬约束）。
- Pinia 六拆分：auth / conversation / message / character / memory / ui；禁止万能 store；stats、admin 用页面级状态。
- SSE 连接管理在 useSseChat() composable，store 只存状态不管连接。
- 打字机渲染节流：每 50ms 批量 flush delta。

---

## 5. 项目目录

### 5.1 Monorepo

```
xinyu/
├── xinyu-web/       前端
├── xinyu-server/    后端
├── docs/            设计文档、模块说明（持续沉淀）
├── scripts/         schema.sql、部署脚本
└── README.md
```

### 5.2 后端（按业务分包，非全局技术分层）

```
com.xinyu
├── common/{result, exception, security, config, util}
├── auth|user|character|conversation|message|memory/{controller, service, mapper, entity, dto, vo, enums}
├── chat/{controller, service(ChatService + ContextAssembler 独立类)}
├── llm/{LlmClient接口, qwen/QwenLlmClient, dto, entity+mapper(AiModel)}
├── stats/{controller, service, vo}
└── admin/{controller, vo}（复用业务 Service）
```
- 命名：入参 XxxDTO / 出参 XxxVO / 实体无后缀 / Service 接口 + impl。
- 配置：application.yml + application-dev.yml(gitignore，含密钥) + application-example.yml(模板入库)。

### 5.3 前端

```
src/
├── api/{request.ts, sse.ts, modules/*}    types/    stores/(六拆分)
├── composables/{useSseChat, useTheme, useClipboard}
├── router/{index, routes, guards}         layouts/{Main, Admin, Blank}
├── views/{auth, chat, square, character, studio, me, admin, NotFound}
├── components/{base, chat, character, common}
├── styles/{tokens.css, base.css, markdown.css}      utils/    assets/
```
- 命名：组件 PascalCase、页面 XxxView.vue、composable useXxx、api 函数动词开头。

### 5.4 Git 规范

- 分支：main(可演示，里程碑合并+tag 如 v0.1-m1) ← dev(日常) ← feature/mX-xxx(仅大功能开分支，小修改直接提 dev)。
- Commit：Conventional Commits 中文描述，如 `feat(chat): 实现SSE流式回复与消息状态机`。

---

## 附录 A · 简历叙事要点（M5 写 README/简历时使用）

- 基于 Spring Boot 构建模块化单体架构，通过业务模块隔离提升可维护性。
- 针对 AI 对话场景设计消息状态机、上下文窗口管理和长期记忆存储模型，实现流式回复过程中的消息一致性。
- 基于 SSE 实现 AI 回复流式传输，使用事件驱动方式优化聊天交互体验（POST + fetch-event-source 方案）。
- 通过 LlmClient 接口抽象降低模型供应商耦合，支持多模型扩展。
- 基于设计 Token 构建统一主题系统，实现 AI 产品级视觉规范。
- 设计 SSE 驱动的实时聊天交互状态机，实现 AI 思考、流式输出、异常恢复等完整体验。
- 使用组件化架构拆分聊天复杂业务模块（单文件 <300 行硬约束），提高前端可维护性。
- 叙事原则：讲"为什么这样设计、遇到什么问题、如何解决"，不写"设计了 10 张表"式数量叙事。

## 附录 B · 开发流程约定

需求分析 → 模块设计 → 数据库设计 → API 设计 → 页面设计 → 编码；
每完成一个模块停止等确认；需求有问题先分析并给 2~3 种方案；
每模块交付同步产出：设计说明 / 数据库说明 / API 文档 / 测试说明 / Commit Message。
