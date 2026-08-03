package com.xinyu.llm;

/**
 * LLM 流式回调
 */
public interface LlmStreamCallback {

    /**
     * 收到一段增量文本
     */
    void onDelta(String delta);

    /**
     * 生成正常结束
     *
     * @param usage 本次调用用量（promptTokens + completionTokens）;
     *              供应商未返回时由实现估算或返回 0
     */
    void onComplete(LlmUsage usage);

    /**
     * 生成失败（连接/超时/内容拦截等）
     */
    void onError(Throwable cause);
}
