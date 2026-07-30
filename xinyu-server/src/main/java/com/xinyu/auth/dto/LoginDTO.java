package com.xinyu.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 登录请求
 *
 * <p>不加格式校验(只查非空): 格式不合法的用户名必然登录失败,
 * 统一走"用户名或密码错误", 避免向攻击者泄露合法用户名的格式规则。
 */
@Data
public class LoginDTO {

    /** 用户名 */
    @NotBlank(message = "用户名不能为空")
    private String username;

    /** 密码 */
    @NotBlank(message = "密码不能为空")
    private String password;
}
