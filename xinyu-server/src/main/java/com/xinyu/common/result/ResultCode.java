package com.xinyu.common.result;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 全局业务状态码枚举
 *
 * <p>分段约定（见 docs/design.md 3.1）：
 * <ul>
 *   <li>0          成功</li>
 *   <li>401xx      认证相关</li>
 *   <li>403xx      权限相关</li>
 *   <li>404xx      资源不存在</li>
 *   <li>422xx      参数校验失败</li>
 *   <li>500xx      系统内部异常</li>
 *   <li>510xx      LLM 大模型调用异常（前端做角色化文案降级）</li>
 * </ul>
 */
@Getter
@AllArgsConstructor
public enum ResultCode {

    /** 成功 */
    SUCCESS(0, "ok"),

    /** 未登录或登录已过期 */
    UNAUTHORIZED(40100, "未登录或登录已过期"),

    /** 无权限访问该资源 */
    FORBIDDEN(40300, "无权限访问"),

    /** 资源不存在（或已被删除） */
    NOT_FOUND(40400, "资源不存在"),

    /** 请求参数校验失败 */
    PARAM_ERROR(42200, "参数校验失败"),

    /** 系统内部异常（兜底） */
    SYSTEM_ERROR(50000, "系统异常，请稍后重试"),

    /** LLM 服务连接失败 */
    LLM_CONNECT_ERROR(51001, "AI 服务连接失败"),

    /** LLM 响应超时 */
    LLM_TIMEOUT(51002, "AI 响应超时"),

    /** 上下文 Token 超出模型限制 */
    LLM_TOKEN_LIMIT(51003, "对话内容过长，请开启新会话"),

    /** 内容被安全策略拦截 */
    LLM_CONTENT_BLOCKED(51004, "内容包含敏感信息，已被拦截");

    /** 业务状态码 */
    private final int code;

    /** 默认提示信息 */
    private final String message;
}
