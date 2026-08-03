package com.xinyu.llm.model.vo;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 模型配置保存请求（添加/编辑共用）
 *
 * <p>apiKey 为明文输入（后端加密入库）:
 * <ul>
 *   <li>添加时必填</li>
 *   <li>编辑时若为 null/空, 保留原 Key; 非空则覆盖</li>
 * </ul>
 */
@Data
public class AiModelSaveRequest {

    @NotBlank(message = "供应商不能为空")
    private String provider;

    @NotBlank(message = "模型标识不能为空")
    private String modelCode;

    @NotBlank(message = "展示名不能为空")
    private String displayName;

    @NotBlank(message = "接口地址不能为空")
    private String baseUrl;

    /** 明文 API Key（添加必填, 编辑可选） */
    private String apiKey;

    /** 是否设为默认模型 */
    private Boolean isDefault;
}
