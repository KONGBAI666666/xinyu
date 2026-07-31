package com.xinyu.llm;

import com.xinyu.llm.dto.LlmMessage;

import java.util.List;

/**
 * LLM 客户端抽象: 屏蔽模型供应商差异（依赖倒置）
 *
 * <p>M1-3 由 {@link com.xinyu.llm.mock.MockLlmClient} 实现,
 * 后续接入通义千问时新增 QwenLlmClient, 聊天编排层零改动。
 */
public interface LlmClient {

    /**
     * 实际使用的模型标识（写入 message.model_code 溯源）
     */
    String modelCode();

    /**
     * 流式对话（同步阻塞式: 方法返回即本次生成结束, 由调用方决定执行线程）
     *
     * <p>回调约定: 正常结束回调 onComplete, 失败回调 onError, 二者互斥且只回调一次;
     * onDelta 抛出的运行时异常（如 SSE 断连）原样向上传播, 实现类不得吞掉。
     *
     * @param messages 完整上下文（system + 历史 + 当前输入）, 由 ContextAssembler 组装
     * @param callback 流式回调
     */
    void streamChat(List<LlmMessage> messages, LlmStreamCallback callback);
}
