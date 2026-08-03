package com.xinyu.rag.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建知识库请求
 */
@Data
public class KnowledgeBaseCreateRequest {

    @NotBlank(message = "知识库名称不能为空")
    @Size(max = 50, message = "名称最长 50 字")
    private String name;

    @Size(max = 200, message = "简介最长 200 字")
    private String description;
}
