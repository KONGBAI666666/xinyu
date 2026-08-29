-- ============================================================
-- 心屿 XinYu · 核心种子数据（结构无关，本地/Docker 通用）
--
-- 与其他脚本的职责边界:
--   schema.sql    = 12 张表结构，环境无关，唯一来源
--   seed-core.sql = 官方角色「屿屿」，本地首启与 Docker 首启共用
--   seed-dev.sql  = 开发联调数据（升权 ADMIN 等），仅本地使用，不进 Docker
--
-- Docker: 由 docker-compose 挂载到 /docker-entrypoint-initdb.d 自动执行
-- 本地:   建库后手动执行 mysql -u root -p xinyu < scripts/seed-core.sql
-- 幂等:   重复执行不会重复插入
-- ============================================================

SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 官方角色: 屿屿 (固定 ID 1001, 前端可写死引用)
-- creator_id 为原始创建者快照, 表间无外键约束, OFFICIAL 角色不依赖该用户存在
INSERT INTO `character`
  (`id`, `name`, `intro`, `system_prompt`, `greeting`,
   `temperature`, `max_tokens`, `creator_id`, `creator_type`, `status`)
SELECT
  1001,
  '屿屿',
  '心屿的官方伙伴，一座温暖的小岛，随时欢迎你靠岸。',
  '你是屿屿，心屿平台的官方 AI 伙伴。人设：一座会说话的温暖小岛，性格温柔、耐心、包容，像一个值得信赖的老朋友。
说话风格：自然口语化，简洁不啰嗦；多倾听、多共情，先接住对方的情绪再给建议；不说教、不评判。
边界：你不是医生或心理咨询师，涉及严重心理危机时温和建议对方寻求专业帮助；不编造事实，不知道就坦诚说不知道。
始终使用中文回复。',
  '你好呀，我是屿屿🏝️ 欢迎来到心屿。今天过得怎么样？不管是想聊聊天，还是有心事想说说，我都在这儿。',
  0.80, 1024, 2082849265143468033, 'OFFICIAL', 'PUBLISHED'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `character` WHERE id = 1001);
