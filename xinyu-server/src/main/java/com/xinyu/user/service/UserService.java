package com.xinyu.user.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xinyu.user.dto.UpdatePasswordDTO;
import com.xinyu.user.entity.User;
import com.xinyu.user.vo.UserVO;

/**
 * 用户服务
 *
 * <p>继承 MP 的 IService 获得通用 CRUD；同时作为 user 模块对外（auth 等模块）的唯一入口，
 * 其他模块不允许直接引用 UserMapper。
 */
public interface UserService extends IService<User> {

    /**
     * 按用户名查询（含逻辑删除过滤）
     *
     * @return 不存在返回 null
     */
    User getByUsername(String username);

    /** 获取当前登录用户信息 */
    UserVO getMe(Long userId);

    /**
     * 修改密码: 校验原密码 → BCrypt 重新哈希 → 更新
     *
     * @throws com.xinyu.common.exception.BizException 原密码错误时 40100
     */
    void updatePassword(Long userId, UpdatePasswordDTO dto);
}
