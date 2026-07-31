package com.xinyu.llm.mock;

import com.xinyu.llm.LlmClient;
import com.xinyu.llm.LlmStreamCallback;
import com.xinyu.llm.dto.LlmMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 模拟 LLM 客户端（M1-3 专用）
 *
 * <p>M1-3 的重点是 SSE 管道、消息状态机、上下文组装与异常处理,
 * 不是 AI 能力本身; 先用 Mock 打通全链路, 真实模型接入时
 * 替换实现类即可（LlmClient 接口不变）。
 *
 * <p>M1-5 起仅 dev 环境注册: 测试永远稳定且零成本;
 * prod 环境由 {@link com.xinyu.llm.qwen.QwenLlmClient} 接真实模型。
 *
 * <p>触发失败: 最后一条 user 消息含 {@link #FAIL_TRIGGER} 时回调 onError,
 * 用于验收"LLM 异常 → event:error → 消息置 FAILED"链路。
 */
@Slf4j
@Profile("dev")
@Component
public class MockLlmClient implements LlmClient {

    /** 测试用失败触发词 */
    public static final String FAIL_TRIGGER = "[MOCK_FAIL]";

    /** 模拟回复分段（每段间隔 SEGMENT_INTERVAL_MS 模拟流式输出） */
    private static final String[] SEGMENTS = {
            "你好呀，", "我是心屿的 AI 伙伴。", "此刻的回复来自 Mock 通道，",
            "SSE 管道、消息状态机都在真实运转。", "接入真实模型后，", "我就能真正听懂你说的话了。"
    };

    private static final long SEGMENT_INTERVAL_MS = 100;

    @Override
    public String modelCode() {
        return "mock";
    }

    @Override
    public void streamChat(List<LlmMessage> messages, LlmStreamCallback callback) {
        String lastUserContent = messages.stream()
                .filter(m -> "user".equals(m.role()))
                .reduce((first, second) -> second)
                .map(LlmMessage::content)
                .orElse("");

        if (lastUserContent.contains(FAIL_TRIGGER)) {
            log.warn("MockLlmClient 命中失败触发词, 模拟 LLM 连接失败");
            callback.onError(new IllegalStateException("mock llm connect failed"));
            return;
        }

        int totalChars = 0;
        for (String segment : SEGMENTS) {
            try {
                Thread.sleep(SEGMENT_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                callback.onError(e);
                return;
            }
            // onDelta 抛出的异常(如SSE断连)按接口约定向上传播, 由编排层处理 STOPPED
            callback.onDelta(segment);
            totalChars += segment.length();
        }
        // Mock 阶段以字符数粗估 token 数
        callback.onComplete(totalChars);
    }
}
