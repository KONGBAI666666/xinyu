package com.xinyu.user.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户实体（对应 scripts/schema.sql 的 user 表）
 *
 * <p>M1-1.2 仅用于 MyBatis-Plus 基础设施验证，
 * 业务字段的读写逻辑在 M1-2 用户认证阶段实现。
 */
@Data
@TableName("`user`")
public class User {

    /** 雪花ID（全局配置 assign_id，插入时由 MP 自动生成） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 登录名：4-16位字母数字下划线 */
    private String username;

    /** BCrypt 密文 */
    private String password;

    /** 昵称，默认同 username */
    private String nickname;

    /** 头像，空则前端默认兜底 */
    private String avatarUrl;

    /** 预留找回密码 */
    private String email;

    /** USER / ADMIN */
    private String role;

    /** ACTIVE / BANNED */
    private String status;

    /** 最近登录时间 */
    private LocalDateTime lastLoginAt;

    /** 创建时间（插入时自动填充） */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间（插入/更新时自动填充） */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /** 逻辑删除：0-正常 1-已删除 */
    @TableLogic
    private Integer deleted;
}
