package com.xinyu.common.exception;

import com.xinyu.common.result.ResultCode;
import lombok.Getter;

/**
 * 业务异常
 *
 * <p>业务代码中主动抛出，由 {@link GlobalExceptionHandler} 统一捕获并转为 {@code Result}。
 * 属于可预期异常，只记 warn 日志、不打堆栈。
 */
@Getter
public class BizException extends RuntimeException {

    /** 业务状态码 */
    private final int code;

    /** 使用状态码默认文案 */
    public BizException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
    }

    /** 使用自定义文案（如"用户名已存在"），状态码取自分段枚举 */
    public BizException(ResultCode resultCode, String message) {
        super(message);
        this.code = resultCode.getCode();
    }
}
