package com.xinyu.memory.service;

import com.xinyu.memory.entity.Memory;
import com.xinyu.memory.enums.MemoryImportance;
import com.xinyu.memory.vo.MemoryUpdateRequest;
import com.xinyu.memory.vo.MemoryVO;

import java.util.List;

/**
 * 长期记忆服务: memory 模块对外唯一入口
 *
 * <p>职责:
 * <ul>
 *   <li>用户视角的 CRUD（列表 / 编辑 / 删除 / 启停）, 不暴露新增接口（提取为服务端内部行为）</li>
 *   <li>注入查询: 按 importance Top-K 取 ACTIVE 记忆, 供 ContextAssembler 注入</li>
 *   <li>批量保存提取结果（由 MemoryExtractor 调用）</li>
 * </ul>
 */
public interface MemoryService {

    /**
     * 列出用户的全部记忆, 可按角色筛选
     *
     * @param userId      用户 ID
     * @param characterId 角色 ID, null 表示不筛选（全部角色）
     */
    List<MemoryVO> listByUser(Long userId, Long characterId);

    /**
     * 注入查询: 按 importance 权重取 Top-K 条 ACTIVE 记忆
     *
     * @param userId      用户 ID
     * @param characterId 角色 ID
     * @param limit       最多条数
     */
    List<Memory> listActiveForInject(Long userId, Long characterId, int limit);

    /** 用户编辑记忆（content / importance / status） */
    MemoryVO update(Long id, Long userId, MemoryUpdateRequest req);

    /** 用户删除记忆 */
    void delete(Long id, Long userId);

    /**
     * 批量保存提取的记忆（MemoryExtractor 调用）
     *
     * @param userId       用户 ID
     * @param characterId  角色 ID
     * @param conversationId 来源会话 ID
     * @param memories     提取出的记忆列表
     */
    void saveExtracted(Long userId, Long characterId, Long conversationId, List<ExtractedMemory> memories);

    /** 提取出的单条记忆（内部 DTO, 不对外暴露） */
    record ExtractedMemory(String memoryKey, String content, MemoryImportance importance) {
    }
}
