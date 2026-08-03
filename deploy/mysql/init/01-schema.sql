-- ============================================================
-- 心屿 XinYu · Docker 首启初始化脚本 (M1-7)
-- 由 mysql 容器 docker-entrypoint-initdb.d 在数据卷为空时自动执行
-- (库 xinyu 已由 MYSQL_DATABASE 环境变量创建, 此处只建表 + 种子数据)
--
-- 内容: 12 张表结构 + 种子数据 (ai_model 默认模型 / character 官方角色屿屿)
-- 不含: 用户账号 / 会话 / 消息 —— 镜像启动必须是干净环境
-- 来源: mysqldump 自本地开发库, 修改表结构请同步本文件
-- ============================================================

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

-- ---------- 表结构 ----------

DROP TABLE IF EXISTS `ai_model`;
CREATE TABLE `ai_model` (
  `id` bigint NOT NULL,
  `user_id` bigint NOT NULL COMMENT '归属用户(个人模型库)',
  `provider` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'QWEN/OPENAI/DEEPSEEK/MOONSHOT/GLM/OLLAMA...',
  `model_code` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '模型标识, 如qwen-plus / gpt-4o / deepseek-chat',
  `display_name` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '展示名, 用户自定义',
  `base_url` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'OpenAI兼容接口地址',
  `api_key_encrypted` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'AES加密后的API Key',
  `is_default` tinyint NOT NULL DEFAULT '0' COMMENT '用户默认模型(每用户至多1个)',
  `enabled` tinyint NOT NULL DEFAULT '1',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_user` (`user_id`,`is_default`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='模型配置表(用户级)';

DROP TABLE IF EXISTS `character`;
CREATE TABLE `character` (
  `id` bigint NOT NULL COMMENT '雪花ID',
  `name` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '角色名',
  `avatar_url` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '角色头像',
  `intro` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '一句话介绍(广场卡片)',
  `system_prompt` text COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '人设核心Prompt',
  `greeting` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '开场白(新会话首条AI消息)',
  `temperature` decimal(3,2) NOT NULL DEFAULT '0.80' COMMENT '采样温度 0~2',
  `max_tokens` int NOT NULL DEFAULT '1024' COMMENT '单次回复上限',
  `model_id` bigint DEFAULT NULL COMMENT '指定模型, 空=系统默认(多模型预留)',
  `creator_id` bigint NOT NULL COMMENT '创建者user.id',
  `creator_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'USER' COMMENT 'OFFICIAL/USER',
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PENDING/PUBLISHED/OFFLINE',
  `chat_count` int NOT NULL DEFAULT '0' COMMENT '冗余计数: 累计会话数(热度排序)',
  `favorite_count` int NOT NULL DEFAULT '0' COMMENT '冗余计数: 收藏数',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_square` (`status`,`creator_type`),
  KEY `idx_creator` (`creator_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI角色表';

DROP TABLE IF EXISTS `character_favorite`;
CREATE TABLE `character_favorite` (
  `id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `character_id` bigint NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_char` (`user_id`,`character_id`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色收藏表';

DROP TABLE IF EXISTS `character_tag`;
CREATE TABLE `character_tag` (
  `id` bigint NOT NULL,
  `character_id` bigint NOT NULL,
  `tag_id` bigint NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_char_tag` (`character_id`,`tag_id`),
  KEY `idx_tag` (`tag_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色标签关联表';

DROP TABLE IF EXISTS `conversation`;
CREATE TABLE `conversation` (
  `id` bigint NOT NULL,
  `user_id` bigint NOT NULL COMMENT '归属用户',
  `character_id` bigint NOT NULL COMMENT '绑定角色',
  `model_id` bigint DEFAULT NULL COMMENT '会话级模型覆盖, NULL=用用户默认模型',
  `kb_id` bigint DEFAULT NULL COMMENT '会话绑定的知识库ID (M3 RAG), NULL=普通聊天',
  `title` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '默认取首条用户消息前20字, 可重命名',
  `last_message_at` datetime DEFAULT NULL COMMENT '冗余: 最新消息时间(列表排序)',
  `last_message_preview` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '冗余: 最新消息摘要(列表副标题)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_user_last` (`user_id`,`last_message_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会话表';

DROP TABLE IF EXISTS `file`;
CREATE TABLE `file` (
  `id` bigint NOT NULL,
  `user_id` bigint NOT NULL COMMENT '上传者',
  `original_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '原始文件名',
  `storage_path` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '存储路径',
  `url` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '访问地址',
  `file_type` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'MIME类型',
  `file_size` bigint DEFAULT NULL COMMENT '字节数',
  `biz_type` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'USER_AVATAR/CHARACTER_AVATAR/ATTACHMENT',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文件表(预留)';

DROP TABLE IF EXISTS `knowledge_base`;
CREATE TABLE `knowledge_base` (
  `id` bigint NOT NULL COMMENT '雪花ID',
  `user_id` bigint NOT NULL COMMENT '归属用户',
  `name` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '知识库名称',
  `description` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '简介',
  `doc_count` int NOT NULL DEFAULT '0' COMMENT '冗余: 文档数',
  `chunk_count` int NOT NULL DEFAULT '0' COMMENT '冗余: 切片数(=Qdrant点数)',
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/PROCESSING/ERROR',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识库表(M3 RAG)';

DROP TABLE IF EXISTS `knowledge_document`;
CREATE TABLE `knowledge_document` (
  `id` bigint NOT NULL COMMENT '雪花ID',
  `kb_id` bigint NOT NULL COMMENT '所属知识库',
  `user_id` bigint NOT NULL COMMENT '冗余: 上传者(权限校验免join)',
  `file_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '原始文件名',
  `file_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'PDF/MARKDOWN/TXT',
  `file_size` bigint NOT NULL COMMENT '字节数',
  `chunk_count` int NOT NULL DEFAULT '0' COMMENT '切成的块数',
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PROCESSING' COMMENT 'PROCESSING/READY/ERROR',
  `error_msg` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '处理失败原因',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_kb` (`kb_id`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识库文档表(M3 RAG, 向量存Qdrant, 此表存元数据)';

DROP TABLE IF EXISTS `memory`;
CREATE TABLE `memory` (
  `id` bigint NOT NULL,
  `user_id` bigint NOT NULL COMMENT '记忆归属用户',
  `character_id` bigint NOT NULL COMMENT '记忆所属角色(角色间记忆隔离)',
  `memory_key` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '结构化键: hobby/job/style等',
  `content` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '记忆正文, 如"用户喜欢Java编程"',
  `importance` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'MEDIUM' COMMENT 'HIGH/MEDIUM/LOW(注入时HIGH优先)',
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/DISABLED(用户可停用不删除)',
  `source_conversation_id` bigint DEFAULT NULL COMMENT '提取来源会话(可溯源)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_inject` (`user_id`,`character_id`,`status`,`importance`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='长期记忆表';

DROP TABLE IF EXISTS `message`;
CREATE TABLE `message` (
  `id` bigint NOT NULL,
  `conversation_id` bigint NOT NULL COMMENT '所属会话',
  `user_id` bigint NOT NULL COMMENT '冗余: 归属用户(统计/权限免join)',
  `parent_message_id` bigint DEFAULT NULL COMMENT '消息树: 重新生成的候选挂同一USER消息下',
  `sequence_no` int NOT NULL COMMENT '会话内自增业务序号(上下文排序依据)',
  `client_message_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '前端UUID, 幂等防重复提交',
  `message_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'USER/ASSISTANT/SYSTEM',
  `content` mediumtext COLLATE utf8mb4_unicode_ci COMMENT 'Markdown原文',
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'COMPLETED' COMMENT 'GENERATING/COMPLETED/FAILED/STOPPED',
  `prompt_tokens` int DEFAULT NULL COMMENT '输入token(仅ASSISTANT)',
  `completion_tokens` int DEFAULT NULL COMMENT '输出token(仅ASSISTANT)',
  `model_code` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '实际使用模型(溯源)',
  `feedback` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'NONE' COMMENT 'NONE/LIKE/DISLIKE',
  `regenerate_count` int NOT NULL DEFAULT '0' COMMENT '重新生成次数',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_client_msg` (`user_id`,`client_message_id`),
  KEY `idx_conv` (`conversation_id`,`id`),
  KEY `idx_user_created` (`user_id`,`created_at`),
  KEY `idx_feedback` (`feedback`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='消息表';

DROP TABLE IF EXISTS `tag`;
CREATE TABLE `tag` (
  `id` bigint NOT NULL,
  `name` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '标签名',
  `sort` int NOT NULL DEFAULT '0' COMMENT '排序权重',
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/HIDDEN',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='标签字典表';

DROP TABLE IF EXISTS `user`;
CREATE TABLE `user` (
  `id` bigint NOT NULL COMMENT '雪花ID',
  `username` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '登录名: 4-16位字母数字下划线(后端校验)',
  `password` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'BCrypt密文',
  `nickname` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '昵称, 默认同username',
  `avatar_url` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '头像, 空则前端默认兜底',
  `email` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '预留找回密码',
  `role` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'USER' COMMENT 'USER/ADMIN',
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/BANNED',
  `last_login_at` datetime DEFAULT NULL COMMENT '最近登录时间',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- ---------- 种子数据 ----------

-- 官方角色: 屿屿 (creator_id 为原始创建者快照, 无外键约束, OFFICIAL 角色不依赖该用户存在)
INSERT INTO `character` VALUES (1001,'屿屿',NULL,'心屿的官方伙伴，一座温暖的小岛，随时欢迎你靠岸。','你是屿屿，心屿平台的官方 AI 伙伴。人设：一座会说话的温暖小岛，性格温柔、耐心、包容，像一个值得信赖的老朋友。\n说话风格：自然口语化，简洁不啰嗦；多倾听、多共情，先接住对方的情绪再给建议；不说教、不评判。\n边界：你不是医生或心理咨询师，涉及严重心理危机时温和建议对方寻求专业帮助；不编造事实，不知道就坦诚说不知道。\n始终使用中文回复。','你好呀，我是屿屿🏝️ 欢迎来到心屿。今天过得怎么样？不管是想聊聊天，还是有心事想说说，我都在这儿。',0.80,1024,NULL,2082849265143468033,'OFFICIAL','PUBLISHED',0,0,'2026-07-30 23:26:44','2026-07-30 23:26:44',0);

/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;
/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;
