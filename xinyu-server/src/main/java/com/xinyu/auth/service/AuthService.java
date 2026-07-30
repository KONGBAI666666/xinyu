package com.xinyu.auth.service;

import com.xinyu.auth.dto.LoginDTO;
import com.xinyu.auth.dto.RegisterDTO;
import com.xinyu.auth.vo.LoginVO;

/**
 * 认证服务: 注册、登录、JWT 签发
 */
public interface AuthService {

    /**
     * 注册（注册即登录, 直接返回 token 减少一次交互）
     *
     * @throws com.xinyu.common.exception.BizException 用户名已存在时 40100
     */
    LoginVO register(RegisterDTO dto);

    /**
     * 登录
     *
     * <p>用户不存在与密码错误统一返回"用户名或密码错误"(40100),
     * 不区分两种情况, 防止用户名枚举攻击。
     */
    LoginVO login(LoginDTO dto);
}
