package com.xinyu.llm.model.service;

import com.xinyu.llm.model.entity.AiModel;
import com.xinyu.llm.model.vo.AiModelSaveRequest;
import com.xinyu.llm.model.vo.AiModelVO;

import java.util.List;

/**
 * AI 模型配置服务（用户级 CRUD + 默认模型管理）
 */
public interface AiModelService {

    /** 列出当前用户的所有模型 */
    List<AiModelVO> listByUser(Long userId);

    /** 获取当前用户的默认模型（无默认返回 null） */
    AiModel getDefaultModel(Long userId);

    /** 按 ID 获取模型（含权限校验: 必须属于该用户） */
    AiModel getByIdAndUser(Long id, Long userId);

    /** 添加模型 */
    AiModelVO create(Long userId, AiModelSaveRequest req);

    /** 编辑模型（apiKey 为空则保留原值） */
    AiModelVO update(Long id, Long userId, AiModelSaveRequest req);

    /** 删除模型（删除默认模型后, 自动选最近一个为默认） */
    void delete(Long id, Long userId);

    /** 设为默认模型（先把该用户其他模型 is_default 置 0） */
    void setDefault(Long id, Long userId);
}
