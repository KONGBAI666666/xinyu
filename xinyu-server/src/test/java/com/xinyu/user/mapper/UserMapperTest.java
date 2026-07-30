package com.xinyu.user.mapper;

import com.xinyu.user.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M1-1.2 MyBatis-Plus 基础设施验证（真实连接 dev 数据库）
 *
 * <p>四项验收：雪花ID自动生成 / 时间自动填充 / deleted 默认 0 / 逻辑删除后查询不到。
 * 测试数据用时间戳用户名隔离，结束后该行保留为 deleted=1 状态（不影响业务）。
 */
@SpringBootTest
class UserMapperTest {

    @Autowired
    private UserMapper userMapper;

    @Test
    void mybatisPlusInfrastructure() {
        // ---------- 准备：只填业务字段，id/时间/deleted 全部交给基础设施 ----------
        User user = new User();
        user.setUsername("mp_test_" + System.currentTimeMillis() % 100000000L);
        user.setPassword("{noop}test");
        user.setNickname("MP验证用户");

        int rows = userMapper.insert(user);
        assertEquals(1, rows, "插入应影响1行");

        // ---------- 验证1：雪花ID自动生成 ----------
        assertNotNull(user.getId(), "insert 后实体应回填雪花ID");
        assertTrue(user.getId() > 0, "雪花ID应为正数");
        System.out.println("[验证1] 雪花ID = " + user.getId());

        // ---------- 验证2：created_at / updated_at 自动填充 ----------
        User saved = userMapper.selectById(user.getId());
        assertNotNull(saved.getCreatedAt(), "created_at 应由 MetaObjectHandler 填充");
        assertNotNull(saved.getUpdatedAt(), "updated_at 应由 MetaObjectHandler 填充");
        System.out.println("[验证2] createdAt = " + saved.getCreatedAt() + ", updatedAt = " + saved.getUpdatedAt());

        // ---------- 验证3：deleted 默认 0 ----------
        assertEquals(0, saved.getDeleted(), "新插入记录 deleted 应为 0");
        System.out.println("[验证3] deleted = " + saved.getDeleted());

        // ---------- 验证4：逻辑删除后查询不到 ----------
        userMapper.deleteById(user.getId());
        User afterDelete = userMapper.selectById(user.getId());
        assertNull(afterDelete, "逻辑删除后 selectById 应返回 null（SQL 自动追加 deleted=0 条件）");
        System.out.println("[验证4] 逻辑删除后 selectById = null ✓（数据库中该行 deleted=1 仍在）");
    }
}
