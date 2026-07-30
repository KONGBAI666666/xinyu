package com.xinyu.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 注册请求
 */
@Data
public class RegisterDTO {

    /** 用户名: 4-16位字母/数字/下划线 (docs/design.md 3.1) */
    @NotBlank(message = "用户名不能为空")
    @Pattern(regexp = "^[a-zA-Z0-9_]{4,16}$", message = "用户名须为4-16位字母、数字或下划线")
    private String username;

    /** 密码: 6-20位 */
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 20, message = "密码长度须为6-20位")
    private String password;

    /** 昵称(可选, 缺省用用户名) */
    @Size(max = 30, message = "昵称最长30个字符")
    private String nickname;
}
