-- ============================================================
-- 心屿 XinYu · 数据库初始化脚本
-- 版本: v1.0 (对应 docs/design.md 第 2 节)
-- 说明: 10 张表一次建齐; M1 只使用 user/character/conversation/
--       message/ai_model, 其余表为后续里程碑预留
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
-- 9. 模型配置表 (多模型预留; API Key不入库, 存配置文件)
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ai_model` (
  `id`           BIGINT       NOT NULL,
  `provider`     VARCHAR(20)  NOT NULL COMMENT 'QWEN/OPENAI/DEEPSEEK...',
  `model_code`   VARCHAR(50)  NOT NULL COMMENT '模型标识, 如qwen-plus',
  `display_name` VARCHAR(50)  NOT NULL COMMENT '展示名',
  `base_url`     VARCHAR(255) NOT NULL COMMENT 'OpenAI兼容接口地址',
  `is_default`   TINYINT      NOT NULL DEFAULT 0 COMMENT '系统默认模型',
  `enabled`      TINYINT      NOT NULL DEFAULT 1,
  `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`      TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_model_code` (`model_code`)
) ENGINE=InnoDB COMMENT='模型配置表';

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

-- ============================================================
-- 初始数据
-- ============================================================

-- 默认模型: 通义千问 (OpenAI兼容模式)
INSERT INTO `ai_model` (`id`, `provider`, `model_code`, `display_name`, `base_url`, `is_default`, `enabled`)
SELECT 1, 'QWEN', 'qwen-plus', '通义千问Plus', 'https://dashscope.aliyuncs.com/compatible-mode/v1', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM `ai_model` WHERE `model_code` = 'qwen-plus');

-- 说明:
-- 1) 管理员账号在 M1-2 认证模块完成后, 通过注册+手工升权或数据脚本创建(密码需BCrypt, 不在此明文预置)
-- 2) 官方默认角色"屿屿"在 M1-3 聊天模块联调时插入, 届时需要真实的管理员creator_id
