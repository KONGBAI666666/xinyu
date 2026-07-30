package com.xinyu.user.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xinyu.common.exception.BizException;
import com.xinyu.common.result.ResultCode;
import com.xinyu.user.dto.UpdatePasswordDTO;
import com.xinyu.user.entity.User;
import com.xinyu.user.mapper.UserMapper;
import com.xinyu.user.service.UserService;
import com.xinyu.user.vo.UserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 用户服务实现
 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private final PasswordEncoder passwordEncoder;

    @Override
    public User getByUsername(String username) {
        return getOne(Wrappers.<User>lambdaQuery().eq(User::getUsername, username));
    }

    @Override
    public UserVO getMe(Long userId) {
        User user = getById(userId);
        if (user == null) {
            // token 有效但用户已被删除(逻辑删除后查不到)
            throw new BizException(ResultCode.NOT_FOUND, "用户不存在");
        }
        return UserVO.from(user);
    }

    @Override
    public void updatePassword(Long userId, UpdatePasswordDTO dto) {
        User user = getById(userId);
        if (user == null) {
            throw new BizException(ResultCode.NOT_FOUND, "用户不存在");
        }
        if (!passwordEncoder.matches(dto.getOldPassword(), user.getPassword())) {
            throw new BizException(ResultCode.UNAUTHORIZED, "原密码错误");
        }
        // 只更新密码字段, updated_at 由 MetaObjectHandler 自动填充
        User update = new User();
        update.setId(userId);
        update.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        updateById(update);
    }
}
