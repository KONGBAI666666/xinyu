package com.xinyu.user.controller;

import com.xinyu.common.result.Result;
import com.xinyu.common.security.UserContext;
import com.xinyu.user.dto.UpdatePasswordDTO;
import com.xinyu.user.service.UserService;
import com.xinyu.user.vo.UserVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户接口（均需登录, 由 AuthInterceptor 保证 UserContext 非空）
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /** 当前登录用户信息 */
    @GetMapping("/me")
    public Result<UserVO> me() {
        return Result.success(userService.getMe(UserContext.getUserId()));
    }

    /** 修改密码 */
    @PutMapping("/me/password")
    public Result<Void> updatePassword(@Valid @RequestBody UpdatePasswordDTO dto) {
        userService.updatePassword(UserContext.getUserId(), dto);
        return Result.success();
    }
}
