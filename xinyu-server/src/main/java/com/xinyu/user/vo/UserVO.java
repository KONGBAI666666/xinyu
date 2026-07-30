package com.xinyu.user.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.xinyu.user.entity.User;
import lombok.Data;

/**
 * 用户信息视图对象（对外脱敏：绝不包含 password 等敏感字段）
 */
@Data
public class UserVO {

    /**
     * 用户ID
     *
     * <p>雪花ID超出 JS Number 安全整数范围(2^53), 直接返回数字前端会丢精度,
     * 序列化为字符串输出。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 用户名 */
    private String username;

    /** 昵称 */
    private String nickname;

    /** 头像URL */
    private String avatarUrl;

    /** 从实体转换（只取对外可见字段） */
    public static UserVO from(User user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setAvatarUrl(user.getAvatarUrl());
        return vo;
    }
}
