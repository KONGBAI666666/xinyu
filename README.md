# 心屿 XinYu

> 每个人心里，都有一座岛。

心屿是一个原创 AI 角色聊天平台，支持 AI 角色、多轮上下文、长期记忆、SSE 流式回复和沉浸式聊天体验。

## 技术栈

| 端 | 技术 |
|---|---|
| 前端 | Vue 3 · TypeScript · Vite · Pinia · Vue Router · Axios · Tailwind CSS |
| 后端 | Java 21 · Spring Boot 3 · MyBatis-Plus · MySQL · JWT · SSE |
| 模型 | 通义千问（OpenAI 兼容协议，接口抽象支持多模型扩展） |

## 项目结构

```
xinyu/
├── xinyu-web/       前端（Vue3 + TS + Vite）
├── xinyu-server/    后端（Spring Boot 3 + Java 21）
├── docs/            技术设计文档
├── scripts/         数据库初始化脚本
└── README.md
```

## 快速开始

详见 [docs/design.md](docs/design.md)。完整 README（架构图、功能演示、部署说明）将在 M5 阶段完善。

> 当前进度：M1 · 可聊天地基（开发中）
