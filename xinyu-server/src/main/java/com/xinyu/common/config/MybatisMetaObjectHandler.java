package com.xinyu.common.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 公共字段自动填充
 *
 * <p>所有实体的 createdAt / updatedAt 由应用层统一写入，
 * 不依赖数据库 DEFAULT CURRENT_TIMESTAMP（保证时间来源一致、便于单测断言）。
 * 实体侧配合 {@code @TableField(fill = FieldFill.INSERT / INSERT_UPDATE)} 生效。
 */
@Component
public class MybatisMetaObjectHandler implements MetaObjectHandler {

    /** 插入时填充：创建时间 + 更新时间 */
    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        this.strictInsertFill(metaObject, "createdAt", LocalDateTime.class, now);
        this.strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, now);
    }

    /** 更新时填充：更新时间 */
    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
    }
}
