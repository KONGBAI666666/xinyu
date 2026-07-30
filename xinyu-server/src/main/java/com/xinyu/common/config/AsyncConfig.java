package com.xinyu.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 异步执行器配置
 *
 * <p>SSE 流式生成不能占用 Tomcat 请求线程（生成期间长时间阻塞会拖垮容器线程池）,
 * 用独立线程池隔离; 队列打满走 CallerRunsPolicy 退化为同步, 不丢任务。
 */
@Configuration
public class AsyncConfig {

    /** 聊天 SSE 推送专用线程池 */
    @Bean("chatExecutor")
    public ThreadPoolTaskExecutor chatExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("chat-sse-");
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
