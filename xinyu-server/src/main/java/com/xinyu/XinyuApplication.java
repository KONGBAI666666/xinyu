package com.xinyu;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * 心屿 XinYu - AI角色聊天平台
 * 启动入口
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class XinyuApplication {

    public static void main(String[] args) {
        SpringApplication.run(XinyuApplication.class, args);
    }
}
