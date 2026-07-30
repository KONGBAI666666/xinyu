package com.xinyu.common.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置
 *
 * <p>雪花ID(assign_id)、逻辑删除字段、驼峰映射均在 application.yml 全局声明，
 * 此处只负责代码级配置：Mapper 扫描 + 分页插件。
 */
@Configuration
@MapperScan("com.xinyu.**.mapper")
public class MybatisPlusConfig {

    /**
     * 分页插件（角色广场、会话列表、管理后台的 page+size 分页依赖此拦截器，
     * 不加的话 Page 查询会退化为全表查询）
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
