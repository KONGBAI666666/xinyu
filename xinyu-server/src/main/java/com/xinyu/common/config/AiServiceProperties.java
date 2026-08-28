package com.xinyu.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Python AI 服务连接配置
 */
@Component
@ConfigurationProperties(prefix = "xinyu.ai-service")
public class AiServiceProperties {

    /** Python AI 服务地址 */
    private String baseUrl = "http://localhost:9100";

    /** 连接超时 (秒) */
    private int connectTimeoutSec = 10;

    /** 读取超时 (秒) */
    private int readTimeoutSec = 180;

    /** 内部接口令牌 (与 Python 侧 AI_INTERNAL_TOKEN 一致; 空表示不启用) */
    private String internalToken = "";

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public int getConnectTimeoutSec() { return connectTimeoutSec; }
    public void setConnectTimeoutSec(int v) { this.connectTimeoutSec = v; }
    public int getReadTimeoutSec() { return readTimeoutSec; }
    public void setReadTimeoutSec(int v) { this.readTimeoutSec = v; }
    public String getInternalToken() { return internalToken; }
    public void setInternalToken(String v) { this.internalToken = v; }
}
