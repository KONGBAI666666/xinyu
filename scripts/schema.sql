-- ============================================================
-- 心屿 XinYu · 数据库初始化脚本
-- 版本: v1.0 (对应 docs/design.md 第 2 节)
-- 说明: 12 张表一次建齐; M1 使用 user/character/conversation/
--       message/ai_model, 其余表 (tag/character_tag/character_favorite/
--       memory/file/knowledge_base/knowledge_document) 随后续里程碑启用
-- 执行: mysql -u root -p < scripts/schema.sql
-- ============================================================

CREATE DATABASE IF NOT EXISTS xinyu
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE xinyu;

-- ------------------------------------------------------------
-- 1. 用户表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `user` (
  `id`            BIGINT       NOT NULL COMMENT '雪花ID',
  `username`      VARCHAR(32)  NOT NULL COMMENT '登录名: 4-16位字母数字下划线(后端校验)',
  `password`      VARCHAR(100) NOT NULL COMMENT 'BCrypt密文',
  `nickname`      VARCHAR(32)  NOT NULL COMMENT '昵称, 默认同username',
  `avatar_url`    VARCHAR(255) DEFAULT NULL COMMENT '头像, 空则前端默认兜底',
  `email`         VARCHAR(64)  DEFAULT NULL COMMENT '预留找回密码',
  `role`          VARCHAR(20)  NOT NULL DEFAULT 'USER' COMMENT 'USER/ADMIN',
  `status`        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/BANNED',
  `last_login_at` DATETIME     DEFAULT NULL COMMENT '最近登录时间',
  `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB COMMENT='用户表';

-- ------------------------------------------------------------
-- 2. AI角色表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `character` (
  `id`             BIGINT        NOT NULL COMMENT '雪花ID',
  `name`           VARCHAR(32)   NOT NULL COMMENT '角色名',
  `avatar_url`     VARCHAR(255)  DEFAULT NULL COMMENT '角色头像',
  `intro`          VARCHAR(200)  NOT NULL COMMENT '一句话介绍(广场卡片)',
  `system_prompt`  TEXT          NOT NULL COMMENT '人设核心Prompt',
  `greeting`       VARCHAR(500)  NOT NULL COMMENT '开场白(新会话首条AI消息)',
  `temperature`    DECIMAL(3,2)  NOT NULL DEFAULT 0.80 COMMENT '采样温度 0~2',
  `max_tokens`     INT           NOT NULL DEFAULT 1024 COMMENT '单次回复上限',
  `model_id`       BIGINT        DEFAULT NULL COMMENT '指定模型, 空=系统默认(多模型预留)',
  `creator_id`     BIGINT        NOT NULL COMMENT '创建者user.id',
  `creator_type`   VARCHAR(20)   NOT NULL DEFAULT 'USER' COMMENT 'OFFICIAL/USER',
  `status`         VARCHAR(20)   NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PENDING/PUBLISHED/OFFLINE',
  `chat_count`     INT           NOT NULL DEFAULT 0 COMMENT '冗余计数: 累计会话数(热度排序)',
  `favorite_count` INT           NOT NULL DEFAULT 0 COMMENT '冗余计数: 收藏数',
  `created_at`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`        TINYINT       NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_square` (`status`, `creator_type`),
  KEY `idx_creator` (`creator_id`)
) ENGINE=InnoDB COMMENT='AI角色表';

-- ------------------------------------------------------------
-- 3. 标签字典表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `tag` (
  `id`         BIGINT      NOT NULL,
  `name`       VARCHAR(20) NOT NULL COMMENT '标签名',
  `sort`       INT         NOT NULL DEFAULT 0 COMMENT '排序权重',
  `status`     VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/HIDDEN',
  `created_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`    TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_name` (`name`)
) ENGINE=InnoDB COMMENT='标签字典表';

-- ------------------------------------------------------------
-- 4. 角色-标签关联表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `character_tag` (
  `id`           BIGINT   NOT NULL,
  `character_id` BIGINT   NOT NULL,
  `tag_id`       BIGINT   NOT NULL,
  `created_at`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_char_tag` (`character_id`, `tag_id`),
  KEY `idx_tag` (`tag_id`)
) ENGINE=InnoDB COMMENT='角色标签关联表';

-- ------------------------------------------------------------
-- 5. 角色收藏表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `character_favorite` (
  `id`           BIGINT   NOT NULL,
  `user_id`      BIGINT   NOT NULL,
  `character_id` BIGINT   NOT NULL,
  `created_at`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_char` (`user_id`, `character_id`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB COMMENT='角色收藏表';

-- ------------------------------------------------------------
-- 6. 会话表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `conversation` (
  `id`                   BIGINT       NOT NULL,
  `user_id`              BIGINT       NOT NULL COMMENT '归属用户',
  `character_id`         BIGINT       NOT NULL COMMENT '绑定角色',
  `model_id`             BIGINT       DEFAULT NULL COMMENT '会话级模型覆盖, NULL=用用户默认模型',
  `kb_id`                BIGINT       DEFAULT NULL COMMENT '会话绑定的知识库ID (M3 RAG), NULL=普通聊天',
  `title`                VARCHAR(50)  NOT NULL COMMENT '默认取首条用户消息前20字, 可重命名',
  `last_message_at`      DATETIME     DEFAULT NULL COMMENT '冗余: 最新消息时间(列表排序)',
  `last_message_preview` VARCHAR(100) DEFAULT NULL COMMENT '冗余: 最新消息摘要(列表副标题)',
  `created_at`           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`              TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_user_last` (`user_id`, `last_message_at`)
) ENGINE=InnoDB COMMENT='会话表';

-- ------------------------------------------------------------
-- 7. 消息表 (核心表)
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `message` (
  `id`                BIGINT      NOT NULL,
  `conversation_id`   BIGINT      NOT NULL COMMENT '所属会话',
  `user_id`           BIGINT      NOT NULL COMMENT '冗余: 归属用户(统计/权限免join)',
  `parent_message_id` BIGINT      DEFAULT NULL COMMENT '消息树: 重新生成的候选挂同一USER消息下',
  `sequence_no`       INT         NOT NULL COMMENT '会话内自增业务序号(上下文排序依据)',
  `client_message_id` VARCHAR(64) DEFAULT NULL COMMENT '前端UUID, 幂等防重复提交',
  `message_type`      VARCHAR(20) NOT NULL COMMENT 'USER/ASSISTANT/SYSTEM',
  `content`           MEDIUMTEXT  COMMENT 'Markdown原文',
  `status`            VARCHAR(20) NOT NULL DEFAULT 'COMPLETED' COMMENT 'GENERATING/COMPLETED/FAILED/STOPPED',
  `prompt_tokens`     INT         DEFAULT NULL COMMENT '输入token(仅ASSISTANT)',
  `completion_tokens` INT         DEFAULT NULL COMMENT '输出token(仅ASSISTANT)',
  `model_code`        VARCHAR(50) DEFAULT NULL COMMENT '实际使用模型(溯源)',
  `feedback`          VARCHAR(20) NOT NULL DEFAULT 'NONE' COMMENT 'NONE/LIKE/DISLIKE',
  `regenerate_count`  INT         NOT NULL DEFAULT 0 COMMENT '重新生成次数',
  `created_at`        DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`        DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`           TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_client_msg` (`user_id`, `client_message_id`),
  KEY `idx_conv` (`conversation_id`, `id`),
  KEY `idx_user_created` (`user_id`, `created_at`),
  KEY `idx_feedback` (`feedback`)
) ENGINE=InnoDB COMMENT='消息表';

-- ------------------------------------------------------------
-- 8. 长期记忆表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `memory` (
  `id`                     BIGINT       NOT NULL,
  `user_id`                BIGINT       NOT NULL COMMENT '记忆归属用户',
  `character_id`           BIGINT       NOT NULL COMMENT '记忆所属角色(角色间记忆隔离)',
  `memory_key`             VARCHAR(50)  DEFAULT NULL COMMENT '结构化键: hobby/job/style等',
  `content`                VARCHAR(500) NOT NULL COMMENT '记忆正文, 如"用户喜欢Java编程"',
  `importance`             VARCHAR(20)  NOT NULL DEFAULT 'MEDIUM' COMMENT 'HIGH/MEDIUM/LOW(注入时HIGH优先)',
  `status`                 VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/DISABLED(用户可停用不删除)',
  `source_conversation_id` BIGINT       DEFAULT NULL COMMENT '提取来源会话(可溯源)',
  `created_at`             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`                TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_inject` (`user_id`, `character_id`, `status`, `importance`)
) ENGINE=InnoDB COMMENT='长期记忆表';

-- ------------------------------------------------------------
-- 9. 模型配置表 (用户级, 每个用户管理自己的模型库; API Key AES加密入库)
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ai_model` (
  `id`                  BIGINT       NOT NULL,
  `user_id`             BIGINT       NOT NULL COMMENT '归属用户(个人模型库)',
  `provider`            VARCHAR(20)  NOT NULL COMMENT 'QWEN/OPENAI/DEEPSEEK/MOONSHOT/GLM/OLLAMA...',
  `model_code`          VARCHAR(50)  NOT NULL COMMENT '模型标识, 如qwen-plus / gpt-4o / deepseek-chat',
  `display_name`        VARCHAR(50)  NOT NULL COMMENT '展示名, 用户自定义',
  `base_url`            VARCHAR(255) NOT NULL COMMENT 'OpenAI兼容接口地址',
  `api_key_encrypted`   VARCHAR(500) DEFAULT NULL COMMENT 'AES加密后的API Key',
  `is_default`          TINYINT      NOT NULL DEFAULT 0 COMMENT '用户默认模型(每用户至多1个)',
  `enabled`             TINYINT      NOT NULL DEFAULT 1,
  `created_at`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`             TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_user` (`user_id`, `is_default`)
) ENGINE=InnoDB COMMENT='模型配置表(用户级)';

-- ------------------------------------------------------------
-- 10. 文件表 (预留, M1不开发接口)
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `file` (
  `id`            BIGINT       NOT NULL,
  `user_id`       BIGINT       NOT NULL COMMENT '上传者',
  `original_name` VARCHAR(255) NOT NULL COMMENT '原始文件名',
  `storage_path`  VARCHAR(255) NOT NULL COMMENT '存储路径',
  `url`           VARCHAR(255) NOT NULL COMMENT '访问地址',
  `file_type`     VARCHAR(50)  DEFAULT NULL COMMENT 'MIME类型',
  `file_size`     BIGINT       DEFAULT NULL COMMENT '字节数',
  `biz_type`      VARCHAR(30)  NOT NULL COMMENT 'USER_AVATAR/CHARACTER_AVATAR/ATTACHMENT',
  `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted`       TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB COMMENT='文件表(预留)';

-- ------------------------------------------------------------
-- 11. 知识库表 (M3 RAG, 用户级)
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `knowledge_base` (
  `id`            BIGINT       NOT NULL COMMENT '雪花ID',
  `user_id`       BIGINT       NOT NULL COMMENT '归属用户',
  `name`          VARCHAR(50)  NOT NULL COMMENT '知识库名称',
  `description`   VARCHAR(200) DEFAULT NULL COMMENT '简介',
  `doc_count`     INT          NOT NULL DEFAULT 0 COMMENT '冗余: 文档数',
  `chunk_count`   INT          NOT NULL DEFAULT 0 COMMENT '冗余: 切片数(=Qdrant点数)',
  `status`        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/PROCESSING/ERROR',
  `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`       TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB COMMENT='知识库表(M3 RAG)';

-- ------------------------------------------------------------
-- 12. 知识库文档表 (M3 RAG, 记录用户上传的每个文档及其切片元数据)
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `knowledge_document` (
  `id`            BIGINT       NOT NULL COMMENT '雪花ID',
  `kb_id`         BIGINT       NOT NULL COMMENT '所属知识库',
  `user_id`       BIGINT       NOT NULL COMMENT '冗余: 上传者(权限校验免join)',
  `file_name`     VARCHAR(255) NOT NULL COMMENT '原始文件名',
  `file_type`     VARCHAR(20)  NOT NULL COMMENT 'PDF/MARKDOWN/TXT',
  `file_size`     BIGINT       NOT NULL COMMENT '字节数',
  `chunk_count`   INT          NOT NULL DEFAULT 0 COMMENT '切成的块数',
  `status`        VARCHAR(20)  NOT NULL DEFAULT 'PROCESSING' COMMENT 'PROCESSING/READY/ERROR',
  `error_msg`     VARCHAR(500) DEFAULT NULL COMMENT '处理失败原因',
  `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`       TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_kb` (`kb_id`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB COMMENT='知识库文档表(M3 RAG, 向量存Qdrant, 此表存元数据)';

-- ============================================================
-- 初始数据
-- ============================================================

-- 说明:
-- 1) 管理员账号在 M1-2 认证模块完成后, 通过注册+手工升权或数据脚本创建(密码需BCrypt, 不在此明文预置)
-- 2) 官方默认角色"屿屿"在 M1-3 聊天模块联调时插入, 届时需要真实的管理员creator_id
