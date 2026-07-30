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
     * @param completionTokens 输出 token 数（Mock 阶段以字符数粗估）
     */
    void onComplete(int completionTokens);

    /**
     * 生成失败（连接/超时/内容拦截等）
     */
    void onError(Throwable cause);
}
