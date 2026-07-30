package com.xinyu.auth.service.impl;

import com.xinyu.auth.dto.LoginDTO;
import com.xinyu.auth.dto.RegisterDTO;
import com.xinyu.auth.service.AuthService;
import com.xinyu.auth.vo.LoginVO;
import com.xinyu.common.exception.BizException;
import com.xinyu.common.result.ResultCode;
import com.xinyu.common.security.JwtUtil;
import com.xinyu.user.entity.User;
import com.xinyu.user.service.UserService;
import com.xinyu.user.vo.UserVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 认证服务实现
 *
 * <p>模块依赖: auth → user(仅经由 UserService) → common, 单向不回环。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserService userService;

    private final PasswordEncoder passwordEncoder;

    private final JwtUtil jwtUtil;

    @Override
    public LoginVO register(RegisterDTO dto) {
        if (userService.getByUsername(dto.getUsername()) != null) {
            // 格式合法但业务规则不允许, 属于校验类错误(42200), 非认证失败
            throw new BizException(ResultCode.PARAM_ERROR, "用户名已存在");
        }

        User user = new User();
        user.setUsername(dto.getUsername());
        // 只存 BCrypt 哈希, 明文密码不落库不打日志
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setNickname(StringUtils.hasText(dto.getNickname()) ? dto.getNickname() : dto.getUsername());
        // 注册即登录, 直接记首次登录时间; role/status 走数据库默认值 USER/ACTIVE
        user.setLastLoginAt(LocalDateTime.now());
        userService.save(user);

        log.info("新用户注册: userId={}, username={}", user.getId(), user.getUsername());
        return buildLoginVO(user);
    }

    @Override
    public LoginVO login(LoginDTO dto) {
        User user = userService.getByUsername(dto.getUsername());
        // 用户不存在与密码错误合并为同一文案, 防用户名枚举
        if (user == null || !passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw new BizException(ResultCode.UNAUTHORIZED, "用户名或密码错误");
        }

        User update = new User();
        update.setId(user.getId());
        update.setLastLoginAt(LocalDateTime.now());
        userService.updateById(update);

        return buildLoginVO(user);
    }

    /** 签发 token 并组装响应 */
    private LoginVO buildLoginVO(User user) {
        String token = jwtUtil.generate(user.getId(), user.getUsername());
        return new LoginVO(token, UserVO.from(user));
    }
}
