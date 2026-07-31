package com.xinyu.llm;

import com.xinyu.common.result.ResultCode;
import lombok.Getter;

/**
 * LLM 调用异常: 携带 510xx 错误码, 供编排层映射为 SSE error 事件
 *
 * <p>与 {@link com.xinyu.common.exception.BizException} 区分:
 * BizException 走全局异常处理器返回 JSON, 本异常只在 LLM 回调链路内传递,
 * 由 ChatServiceImpl 的 onError 转成 event:error 推送。
 */
@Getter
public class LlmException extends RuntimeException {

    /** 对应 ResultCode 的 51001~51004 */
    private final ResultCode resultCode;

    public LlmException(ResultCode resultCode, String detail) {
        super(detail);
        this.resultCode = resultCode;
    }

    public LlmException(ResultCode resultCode, Throwable cause) {
        super(resultCode.getMessage(), cause);
        this.resultCode = resultCode;
    }
}
