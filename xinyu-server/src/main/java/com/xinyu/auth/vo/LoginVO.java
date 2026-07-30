package com.xinyu.auth.vo;

import com.xinyu.user.vo.UserVO;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 登录/注册成功响应: token + 用户信息
 */
@Data
@AllArgsConstructor
public class LoginVO {

    /** JWT access_token (有效期2h, 前端存后放 Authorization: Bearer) */
    private String token;

    /** 当前用户信息 */
    private UserVO user;
}
