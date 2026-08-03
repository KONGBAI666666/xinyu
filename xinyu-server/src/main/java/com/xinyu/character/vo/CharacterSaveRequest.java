package com.xinyu.character.vo;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 角色保存请求（创建 + 编辑共用）
 *
 * <p>校验规则:
 * <ul>
 *   <li>name / systemPrompt / greeting 必填</li>
 *   <li>temperature 范围 0~2</li>
 *   <li>maxTokens 范围 1~8192</li>
 * </ul>
 */
@Data
public class CharacterSaveRequest {

    @NotBlank(message = "角色名不能为空")
    private String name;

    /** 头像 URL, 可空（前端默认兜底） */
    private String avatarUrl;

    /** 一句话介绍, 可空 */
    private String intro;

    @NotBlank(message = "人设 Prompt 不能为空")
    private String systemPrompt;

    @NotBlank(message = "开场白不能为空")
    private String greeting;

    @NotNull(message = "采样温度不能为空")
    @DecimalMin(value = "0.0", message = "温度不能小于 0")
    @DecimalMax(value = "2.0", message = "温度不能大于 2")
    private java.math.BigDecimal temperature;

    @NotNull(message = "maxTokens 不能为空")
    private Integer maxTokens;

    /** 状态: DRAFT / PUBLISHED / OFFLINE; 创建时可不传, 默认 DRAFT */
    private String status;
}
