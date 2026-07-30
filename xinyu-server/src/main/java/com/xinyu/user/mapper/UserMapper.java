package com.xinyu.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xinyu.user.entity.User;

/**
 * 用户 Mapper
 *
 * <p>继承 BaseMapper 获得基础 CRUD；自定义 SQL 按需加在 resources/mapper/UserMapper.xml。
 */
public interface UserMapper extends BaseMapper<User> {
}
