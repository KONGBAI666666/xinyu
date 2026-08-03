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

    /**
     * 记忆提取专用线程池
     *
     * <p>与 chatExecutor 隔离: 提取是低优先级后台任务, 不能挤占 SSE 推送线程;
     * 单线程 + 大队列即可（提取耗时但无并发要求）; 拒绝策略用 discard 静默丢弃,
     * 队列满时放弃本次提取, 下一轮对话仍会再触发, 不影响主链路。
     */
    @Bean("memoryExecutor")
    public ThreadPoolTaskExecutor memoryExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("memory-extract-");
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.DiscardPolicy());
        executor.initialize();
        return executor;
    }
}
