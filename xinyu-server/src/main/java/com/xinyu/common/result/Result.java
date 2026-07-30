package com.xinyu.common.result;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 统一响应结构
 *
 * <p>所有接口（含异常）统一返回 {@code { code, message, data }}，
 * HTTP 状态码保持 200，业务状态由 code 表达（SSE 流式接口除外）。
 *
 * @param <T> 业务数据类型
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> {

    /** 业务状态码，0 表示成功 */
    private int code;

    /** 提示信息 */
    private String message;

    /** 业务数据，失败时为 null */
    private T data;

    /** 成功（无数据） */
    public static <T> Result<T> success() {
        return success(null);
    }

    /** 成功（携带数据） */
    public static <T> Result<T> success(T data) {
        return new Result<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), data);
    }

    /** 失败（使用状态码默认文案） */
    public static <T> Result<T> fail(ResultCode resultCode) {
        return new Result<>(resultCode.getCode(), resultCode.getMessage(), null);
    }

    /** 失败（自定义提示信息，用于业务异常携带具体原因） */
    public static <T> Result<T> fail(int code, String message) {
        return new Result<>(code, message, null);
    }
}
