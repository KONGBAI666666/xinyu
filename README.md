# 心屿 XinYu

> 每个人心里，都有一座岛。

[![CI](https://github.com/KONGBAI666666/xinyu/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/KONGBAI666666/xinyu/actions/workflows/ci.yml)

心屿是一款 **Python FastAPI 单体后端** 架构的 AI 对话平台：业务后端与 AI 能力统一在 **Python FastAPI** 中实现（架构重构前的 Java Spring Boot 业务层 + 独立 Python AI 服务已合并）。前端基于 **Vue 3**，支持 **SSE 流式聊天、RAG 知识库增强生成、多模型热切换、长期记忆、工具调用 Agent、角色 UGC 广场与世界书**，桌面与移动端自适应，Docker Compose 一键部署（四容器编排）。

```bash
docker compose up -d --build   # 一条命令启动整套系统 → http://localhost
```

## ✨ 功能特性

### 核心聊天
- **SSE 流式回复**：Token 级增量返回，边生成边显示，支持随时停止生成
- **消息状态机**：`GENERATING → COMPLETED / FAILED / STOPPED` 全生命周期管理，中断可容错恢复
- **Markdown 渲染**：代码高亮（highlight.js）、列表、表格，DOMPurify 白名单过滤防御 XSS
- **多轮上下文**：角色 System Prompt + 历史 + 当前输入，服务端组装

### 多模型热切换
- **用户自管理模型**：每个用户可添加任意 OpenAI 兼容模型（Qwen/DeepSeek/GLM/Moonshot/Ollama），API Key 采用 **AES-GCM 加密存储**
- **会话级模型覆盖**：优先级 = 会话级覆盖 > 用户默认模型；聊天顶栏一键切换，无需重新部署
- **进程内直调**：后端解密 API Key 后由同一进程内的 LLM 客户端直接调用，无跨服务 HTTP 开销

### 长期记忆系统
- **自动提取**：每轮 ASSISTANT 完成后，异步任务从对话中提取结构化记忆（JSON 输出 + 容错解析），失败静默不影响主链路
- **Top-K 注入**：按 importance（HIGH/MEDIUM/LOW）权重截断 Top-5，拼接到 system prompt 末尾
- **同 key 去重**：同 (用户, 角色, memory_key) 已有则更新，避免堆积
- **用户可管理**：记忆管理页支持编辑 / 启停 / 删除，按角色筛选

### 角色 UGC 生态
- **角色 CRUD + Prompt 编辑器**：用户自定义人设、开场白、采样温度、maxTokens
- **状态机**：`DRAFT → PUBLISHED → OFFLINE`，草稿仅自己可见，发布后广场可见
- **角色广场**：游客可逛，支持搜索 + 推荐/热门/最新三排序；点击收藏 / 开聊，操作引导登录
- **角色详情页**：模糊头像背景 + 开场白预览 + 悬浮操作栏
- **记忆按 用户 × 角色 隔离**：不同角色不串味

### RAG 知识库增强生成
- **文档上传与向量化**：支持 PDF / Markdown / TXT 三种格式，解析后按 1000 字/块（50 字重叠避免切断语义）切片，调用 Embedding 模型批量向量化，存入 **Qdrant** 向量数据库
- **会话绑定知识库**：创建会话时可选绑定一个知识库，不同会话独立挂载
- **Top-K 检索注入**：用户提问时，按问题 embedding 在 Qdrant 中做 Cosine 相似度检索，取 Top-3 且分数 ≥ 0.5 的片段拼接到 system prompt
- **失败降级**：Qdrant 不可用或检索异常时静默降级为普通聊天，不阻断主链路
- **可视化知识库管理**：知识库卡片列表 / 创建抽屉 / 详情抽屉（文档列表 + 上传进度 + 状态机 + 失败原因展示）

### 用户系统
- 注册 / 登录 / JWT 鉴权 / 接口权限校验 / 数据按用户隔离
- 用量统计：调用次数 + Token 消耗 + 估算成本，实时面板

### 工具调用 Agent
- **Function Calling 多轮循环**：模型请求工具 → 受控执行 → 结果回灌 → 续写，轮次上限 3 轮防止失控，末轮强制收敛
- **MVP 工具集**（只读/用户域内/无外部副作用）：`get_current_datetime` 当前时间、`calculate` 数学计算（**ast 白名单求值**，拒绝函数调用与属性访问注入）、`search_history` 历史消息检索（强制 user_id 过滤）
- **透明可观测**：工具执行以 `tool` 事件推送到前端，气泡内展示调用轨迹；异常详情只进日志不回显

### 会话管理与消息流
- **会话管理**：删除（逻辑删除级联消息）/ 重命名 / **置顶**（置顶组在列表最前，组内按时间倒序）
- **消息游标分页**：`before` 游标加载更早历史，首屏只拉最新一页
- **重新生成**：对最后一条回复一键重 roll（后端截断后重新走完整生成链路）
- **点赞点踩**：ASSISTANT 消息级反馈，再点取消
- **引用溯源**：RAG 命中片段以可折叠卡片展示（来源文档/分块/相似度分数）

### 世界书
- **角色知识条目**：为角色维护设定集（Lorebook），注入到对话上下文，扩展人设的深度设定

### 个性化与多端
- **头像上传**：用户与角色头像，**魔数嗅探白名单**（JPEG/PNG/WEBP，不信任 Content-Type）、2MB 上限、服务端生成文件名杜绝路径穿越
- **移动端适配**：侧栏抽屉化、气泡放宽、触摸友好间距（Tailwind 响应式变体实现）

## 🏗 系统架构

```mermaid
flowchart TB
    Browser["浏览器<br/>Vue 3 + Pinia + Tailwind CSS"]
    Nginx["Nginx<br/>静态资源托管 + /api 反向代理<br/>(SSE: proxy_buffering off)"]
    Backend["Python FastAPI 单体后端 (9000)<br/>用户/认证/角色/会话/消息/统计<br/>LLM · RAG · Memory · Embedding"]
    Qdrant[("Qdrant<br/>向量库 (Cosine)<br/>payload: kb_id/doc_id/text")]
    MySQL[("MySQL 8<br/>用户 / 角色 / 会话 / 消息<br/>模型 / 记忆 / 收藏 / 知识库")]

    Browser -- "HTTP / SSE" --> Nginx
    Nginx -- "/api → :9000" --> Backend
    Backend --> MySQL
    Backend --> Qdrant
    Backend -- "OpenAI 兼容协议" --> LLM["大模型 API<br/>(Qwen / DeepSeek / ...)"]
```

**部署形态**：`docker compose up -d` 拉起四个容器 —— `xinyu-nginx`（对外唯一入口 80）→ `xinyu-backend`（内部 9000, FastAPI 单体）+ `xinyu-mysql`（内部 3306）+ `xinyu-qdrant`（内部 6333 REST），数据全部持久化于 named volume。

### 为什么合并成 Python 单体？

| 维度 | 双服务架构的问题 | 单体后的收益 |
|------|----------------|-------------|
| 部署复杂度 | 两套镜像/依赖/健康检查 | 一个镜像、一条链路 |
| 调用链路 | Java→Python HTTP 中转（SSE 双层转发、超时叠加） | 进程内直调，链路减半 |
| 数据一致性 | 聊天事务跨两个服务，失败补偿复杂 | 单库单事务 |
| 迭代速度 | 跨语言改一个功能要动两处 | Pydantic + SQLAlchemy 全链路类型安全 |

业务与 AI 能力同属一个进程，通过 `api / services / repositories / ai` 分层保持职责清晰；数据库结构与 API 契约和原 Java 版完全兼容，存量数据无缝迁移。

## 🔄 SSE 流式对话链路

```mermaid
sequenceDiagram
    participant U as 前端 (fetch-event-source)
    participant B as FastAPI 后端
    participant L as LLM API
    participant D as MySQL

    U->>B: POST /api/conversations/{id}/chat
    B->>D: 保存 USER 消息 (COMPLETED)
    B->>D: 创建 ASSISTANT 占位消息 (GENERATING)
    B-->>U: 建立 SSE 连接 (meta 事件回执双消息 ID)
    B->>L: 携带人设+上下文+记忆+RAG调用大模型
    loop 流式生成
        L-->>B: token 增量 (或 tool_calls 请求)
        B-->>U: event: delta（前端实时追加渲染）/ event: tool（工具调用轨迹）
    end
    B->>D: 全文落库, 状态 → COMPLETED
    B-->>U: event: done (含 token 用量)
    Note over B,D: 异步触发记忆提取 → 落库 MySQL
```

### ASSISTANT 消息状态机

```mermaid
stateDiagram-v2
    [*] --> GENERATING: SSE 建立时先落占位
    GENERATING --> COMPLETED: 正常结束
    GENERATING --> FAILED: LLM / 服务异常
    GENERATING --> STOPPED: 用户停止 / 前端断连
```

占位先行 + 状态流转的设计保证了**任何异常路径（模型超时、用户断网、主动停止）都不会产生脏数据**，刷新页面后历史消息始终一致。

## 📚 RAG 知识库链路

```mermaid
flowchart LR
    subgraph 入库
        U1["document_parser<br/>文本抽取 (PDF/MD/TXT)"] --> U2["chunk_splitter<br/>1000字/块 + 50字重叠"]
        U2 --> U3["embedding_service<br/>Embedding API"]
        U3 --> U4["qdrant_service<br/>批量 upsert"]
        U4 --> Q[("Qdrant")]
    end

    subgraph Chat 检索
        C1["用户提问"] --> C2["rag_service.search<br/>问题向量化 + Cosine Top-K"]
        C2 --> Q
        Q --> C3["chat_service<br/>system + memory + RAG + 历史"]
        C3 --> C4["llm/client<br/>SSE 流式回复"]
    end
```

**入库**：接收上传 → 元数据落 MySQL → 解析 → 分块 → Embedding → Qdrant upsert。

**检索**：聊天时按会话绑定的 `kb_id` + 用户问题做检索 + 注入 system prompt → LLM 生成。**RAG 失败时静默降级**，聊天链路不受影响。

## 🧰 技术栈

| 层 | 技术 |
|---|---|
| 前端 | Vue 3.5 · TypeScript · Vite · Pinia · Vue Router · Axios · Tailwind CSS 4 · marked + highlight.js + DOMPurify |
| 后端 | Python 3.12+ · FastAPI · Pydantic · SQLAlchemy 2.x (async) · PyJWT · bcrypt · httpx |
| AI 能力 | OpenAI SDK · Qdrant Client · PyMuPDF（同进程, 无跨服务调用） |
| 数据 | MySQL 8（业务元数据） · Qdrant 1.12（向量检索, REST 协议） |
| 模型 | 任意 OpenAI 兼容 API（对话）；Embedding 按默认模型服务商自动映射（通义 / OpenAI / 智谱），可环境变量覆盖 |
| 部署 | Docker Compose · Nginx · Uvicorn |
| 工程质量 | ruff（lint+format） · ESLint 9 + Prettier · GitHub Actions CI · Alembic 迁移 · OpenAPI 契约代码生成 |

## 🚀 快速开始

### 方式一：Docker 一键部署（推荐）

前置：安装 [Docker Desktop](https://www.docker.com/products/docker-desktop/)（或 Linux 下 Docker Engine + Compose 插件）。

```bash
# 1. 准备环境变量（数据库密码 / JWT 密钥）
cp .env.example .env   # Windows: copy .env.example .env
#    编辑 .env 填入数据库密码和 JWT 密钥

# 2. 一键构建并启动（首次构建需拉取基础镜像, 约 3~5 分钟）
docker compose up -d --build

# 3. 浏览器打开
http://localhost
```

MySQL 首次启动自动执行 `scripts/schema.sql`（建表，唯一结构来源）+ `scripts/seed-core.sql`（官方角色屿屿），无需手动建库。停止：`docker compose down`（数据保留在 named volume）。

> 登录后进入「模型管理」添加你的 AI 模型（API Key），设为默认模型即可开始聊天。

### 方式二：本地开发（Windows 一键脚本，推荐）

```powershell
.\start.ps1
# 自动按序启动: Qdrant(7333) → FastAPI 后端(9200) → Vite 前端(5280), 并打开浏览器
# 已在运行的组件自动跳过; 日志写入 logs/
```

### 方式三：手动本地开发

```bash
# 1. 后端（端口 9200，需本机 MySQL 8；环境变量见 app/core/config.py）
cd xinyu-backend
python -m venv .venv
.venv\Scripts\activate          # Windows
pip install -r requirements.txt
set XINYU_ENV=dev               # dev 环境: 未配置模型时降级 mock
set XINYU_DB_PASSWORD=<你的MySQL密码>
python -m uvicorn app.main:app --port 9200 --reload

# 运行单元测试（88 个: 安全兼容性 / 雪花ID / 分块器 / 工具Agent / 文件服务 ...）
python -m pytest tests -q

# 2. 前端（/api 自动代理到 9200）
cd xinyu-web
npm install
npm run dev   # :5280
```

浏览器访问 `http://localhost:5280`。

## 🔄 质量保障

- **测试**：后端 88 个单元测试（安全跨语言兼容 / 雪花ID / 分块器 / 限流器 / 工具 Agent / 文件服务 / 上下文组装），前端 24 个（SSE 流 / store 逻辑）
- **代码规范**：后端 ruff（lint + format），前端 ESLint 9 + Prettier，CI 强制执行
- **契约保护**：`scripts/dump_openapi.py` 导出 OpenAPI → `openapi-typescript` 生成前端类型，CI 双向 diff 拦截契约漂移（后端改动未同步契约 / 前端生成文件过期都会挂）
- **CI**：GitHub Actions 双 job（后端测试 + lint + 契约校验 / 前端测试 + lint + typecheck + 构建契约校验 + build），push 与 PR 均触发

## 📁 项目结构

```
xinyu/
├── xinyu-web/            前端（Vue3 + TS + Vite, 含 Dockerfile: Node 构建 → Nginx 托管）
│   └── src/types/api.generated.ts   OpenAPI 生成的契约类型 (CI 校验与后端同步)
├── xinyu-backend/        Python 单体后端（FastAPI, 含 Dockerfile + 88 个单元测试）
│   ├── app/
│   │   ├── api/          路由层（auth / users / characters / conversations / messages / memories / models / knowledge_bases / stats）
│   │   ├── services/     业务逻辑（chat_service 聊天编排 / file_service 文件上传 / ...）
│   │   ├── repositories/ 数据访问层（SQLAlchemy 查询, 对应原 MyBatis-Plus Mapper）
│   │   ├── models/       SQLAlchemy 实体（与 MySQL 表一一对应）
│   │   ├── schemas/      Pydantic 请求/响应模型（对应原 Java DTO/VO）
│   │   ├── ai/           AI 能力层（llm / rag / memory / prompt / tools 工具Agent, 进程内调用）
│   │   ├── core/         配置 / 安全（JWT / AES-GCM / BCrypt）/ 数据库 / 雪花ID / 异常
│   │   └── main.py       FastAPI 入口（路由组装 + 静态文件挂载 + 全局异常处理）
│   ├── migrations/       Alembic 迁移（baseline + 增量: 唯一索引 / 置顶字段）
│   └── tests/            单元测试
├── nginx/nginx.conf      反向代理配置（SSE 关闭缓冲 / SPA fallback / 静态资源缓存）
├── scripts/              SQL 脚本（schema.sql 结构唯一来源 / seed-core.sql 官方角色 / seed-dev.sql 开发数据）
├── uploads/              用户上传文件（头像等, 本地存储, gitignore）
├── .github/workflows/    CI（后端测试+lint+契约校验 / 前端测试+lint+构建）
├── docker-compose.yml    四容器编排（nginx / backend / mysql / qdrant）
├── .env.example          环境变量模板
└── docs/                 设计文档 + 契约文档 + 讲解文档 + 截图
```

## 🔌 API 概览

### 前端 → 后端（业务 API）

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/auth/register` | 注册（成功即签发 JWT） |
| POST | `/api/auth/login` | 登录 |
| GET | `/api/users/me` | 当前用户信息 |
| GET / POST | `/api/conversations` | 会话列表 / 创建会话 |
| PUT | `/api/conversations/{id}/title` | 修改会话名称 |
| GET | `/api/conversations/{id}/messages` | 历史消息 |
| POST | `/api/conversations/{id}/chat` | 发送消息并建立 **SSE 流式**回复 |
| POST | `/api/conversations/{id}/stop` | 停止生成（后端断开 LLM, 已生成文本保留） |
| POST | `/api/conversations/{id}/regenerate` | 重新生成最后一条回复 |
| PUT | `/api/conversations/{id}/pinned` | 置顶 / 取消置顶会话 |
| PUT | `/api/conversations/{id}/messages/{messageId}/feedback` | 消息点赞点踩 |
| PUT | `/api/users/me/avatar` | 上传用户头像（魔数嗅探, ≤2MB） |
| PUT | `/api/characters/{id}/avatar` | 上传角色头像（仅创建者） |
| GET / PUT | `/api/characters/{id}/lorebook` | 世界书条目管理 |
| GET / POST / PUT / DELETE | `/api/models` | 模型 CRUD（用户自管理） |
| PUT | `/api/conversations/{id}/model` | 会话级模型覆盖 |
| GET / PUT / DELETE | `/api/memories` | 长期记忆管理 |
| GET / POST / PUT / DELETE | `/api/characters` | 角色 CRUD |
| GET | `/api/characters/square` | 角色广场（游客可访问） |
| POST / DELETE | `/api/characters/{id}/favorite` | 收藏 / 取消收藏 |
| GET / POST / DELETE | `/api/knowledge-bases` | 知识库 CRUD |
| GET | `/api/knowledge-bases/{id}` | 知识库详情（含文档列表） |
| POST | `/api/knowledge-bases/{id}/documents` | 上传文档（解析 + 分块 + 向量化一站式处理） |
| DELETE | `/api/knowledge-bases/{id}/documents/{docId}` | 删除文档（元数据 + 向量级联） |
| GET | `/api/stats/usage` | 用量统计 |

除 `/api/auth/**`、`/api/characters/square`、`/api/characters/*/detail` 外所有接口需携带 `Authorization: Bearer <token>`。

## 💡 工程亮点

1. **Python 单体分层架构** —— `api → services → repositories → models` 职责清晰的四层 + 进程内 `ai` 能力层，无跨服务 HTTP 开销；Pydantic + SQLAlchemy 2.x async 全链路类型安全。
2. **跨语言架构迁移** —— 原 Java Spring Boot + 独立 Python AI 服务合并为单体，数据库结构 / API 契约 / JWT / AES 密文 / BCrypt 哈希全部保持兼容，存量数据无缝迁移（单元测试用 Java 真实工具类生成的向量实证互通性）。
3. **多模型热切换** —— 模型配置管理 + API Key AES-GCM 解密后由进程内 LLM 客户端直调，支持任意 OpenAI 兼容模型。
4. **长期记忆系统** —— 每轮对话后异步提取结构化记忆，按 importance Top-K 注入 system prompt；失败静默降级。
5. **角色 UGC 全闭环** —— CRUD + 状态机 + 广场搜索 + 收藏，记忆按 用户×角色 隔离不串味。
6. **RAG 检索增强生成** —— 完整 RAG 管道：文档解析 → 分块 → Embedding → Qdrant 存储 → 检索 → 注入，检索失败静默降级为普通聊天。
7. **SSE 全链路打通** —— FastAPI `StreamingResponse` → Nginx `proxy_buffering off` → 前端 `fetch-event-source` 增量消费；`clientMessageId` 幂等防重复提交。
8. **消息状态机容错** —— ASSISTANT 消息先落 `GENERATING` 占位再生成，异常/停止路径均有终态，杜绝半途中断产生的脏数据。
9. **AI 输出安全** —— 模型输出视为不可信输入：marked 渲染 → DOMPurify 白名单过滤 → v-html 展示。
10. **工具 Agent 安全收敛** —— function calling 受限轮次（上限 3, 末轮强制无工具收敛, 协议异常兜底发 done 防前端悬空）；`calculate` 用 ast 白名单求值，函数调用/属性访问/超大指数一律拒绝。
11. **上传安全** —— 头像上传走魔数嗅探白名单而非信任 Content-Type，服务端生成存储文件名（客户端原始名仅留痕），杜绝路径穿越。
12. **契约即代码** —— 后端 FastAPI 是 API 契约唯一真源：离线导出 OpenAPI → 生成前端 TS 类型 → CI 双向 diff 拦截漂移，前后端类型永不过期。
13. **工程化工具链** —— ruff（lint+format）与 ESLint+Prettier 进 CI；后端 88 + 前端 24 测试全绿。

## 📄 更多文档

- [架构审查报告](docs/architecture-review.html)
