-- ============================================================
-- 心屿 XinYu · 开发环境种子数据（仅 dev, 不进生产）
--
-- 与 schema.sql 的职责边界:
--   schema.sql  = 结构(table/index/constraint), 环境无关
--   seed-dev.sql = 环境数据(官方角色/测试数据), 仅开发联调用
--
-- 执行前提（顺序不能乱）:
--   1. 通过 POST /api/auth/register 注册一个账号作为管理员
--   2. 修改下方 @admin_username 为该账号用户名
--   3. 执行本脚本: 自动升权 ADMIN + 插入官方角色「屿屿」
--
-- 执行方式（PowerShell, 避免管道乱码用 source）:
--   mysql -u root -p --default-character-set=utf8mb4
--   mysql> USE xinyu;
--   mysql> SOURCE e:/虚拟C盘/AI聊天项目/scripts/seed-dev.sql;
-- ============================================================

-- 统一连接排序规则, 避免 MySQL 8 默认 utf8mb4_0900_ai_ci 与表列 unicode_ci 比较报 1267
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

-- ⚠ 改成你注册的管理员用户名
SET @admin_username = 'CHANGE_ME';

SELECT id INTO @admin_id FROM `user`
WHERE username = @admin_username AND deleted = 0;

-- 用户名不存在时 @admin_id 为 NULL, 下方 INSERT 会因 NOT NULL 约束失败, 属预期防御
UPDATE `user` SET role = 'ADMIN' WHERE id = @admin_id;

-- 官方角色「屿屿」: 固定 ID 1001（可读、便于前端 M1-4 写死引用; 避开测试用例占用的 1）
-- 幂等: 重复执行不重复插入
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
  0.80, 1024, @admin_id, 'OFFICIAL', 'PUBLISHED'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `character` WHERE id = 1001);

-- 验证
SELECT id, name, creator_type, status FROM `character` WHERE id = 1001;
