package com.xinyu.memory.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.xinyu.character.entity.AiCharacter;
import com.xinyu.character.service.CharacterService;
import com.xinyu.common.exception.BizException;
import com.xinyu.common.result.ResultCode;
import com.xinyu.memory.entity.Memory;
import com.xinyu.memory.enums.MemoryImportance;
import com.xinyu.memory.enums.MemoryStatus;
import com.xinyu.memory.mapper.MemoryMapper;
import com.xinyu.memory.service.MemoryService;
import com.xinyu.memory.vo.MemoryUpdateRequest;
import com.xinyu.memory.vo.MemoryVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 长期记忆服务实现
 *
 * <p>关键设计:
 * <ul>
 *   <li>角色名冗余: 列表查询后批量补 character.name, 避免每条 JOIN</li>
 *   <li>注入查询: 状态=ACTIVE + 按 importance 权重降序 + created_at 降序, LIMIT K</li>
 *   <li>提取去重: 同 (userId, characterId, memoryKey) 已有则更新 content, 避免同 key 堆积;
 *       memoryKey 为 null 的直接插入（不做去重, 由用户在管理页清理）</li>
 * </ul>
 *
 * <p>{@link CharacterService} 用 {@link Lazy} 注入, 打断 memory ↔ character 潜在循环依赖。
 */
@Slf4j
@Service
public class MemoryServiceImpl implements MemoryService {

    private final MemoryMapper memoryMapper;

    /** dev 环境无 character 数据时兜底; 用 @Lazy 避免潜在循环依赖 */
    @Lazy
    @Autowired
    private CharacterService characterService;

    public MemoryServiceImpl(MemoryMapper memoryMapper) {
        this.memoryMapper = memoryMapper;
    }

    @Override
    public List<MemoryVO> listByUser(Long userId, Long characterId) {
        LambdaQueryWrapper<Memory> qw = Wrappers.<Memory>lambdaQuery()
                .eq(Memory::getUserId, userId)
                .eq(characterId != null, Memory::getCharacterId, characterId)
                .orderByDesc(Memory::getImportance)
                .orderByDesc(Memory::getCreatedAt);
        List<Memory> memories = memoryMapper.selectList(qw);
        if (memories.isEmpty()) {
            return List.of();
        }
        // 批量查角色名, 避免每条 JOIN
        Map<Long, String> characterNames = new HashMap<>();
        for (Memory m : memories) {
            characterNames.computeIfAbsent(m.getCharacterId(), cid -> {
                AiCharacter ch = characterService.getById(cid);
                return ch != null ? ch.getName() : "已删除角色";
            });
        }
        return memories.stream().map(m -> toVO(m, characterNames.get(m.getCharacterId()))).toList();
    }

    @Override
    public List<Memory> listActiveForInject(Long userId, Long characterId, int limit) {
        // importance 在 Java 枚举里映射为权重, 但 DB 里是字符串, 直接按字符串字典序不正确。
        // 用 ORDER BY FIELD(importance,'HIGH','MEDIUM','LOW') 兼容 MySQL。
        LambdaQueryWrapper<Memory> qw = Wrappers.<Memory>lambdaQuery()
                .eq(Memory::getUserId, userId)
                .eq(Memory::getCharacterId, characterId)
                .eq(Memory::getStatus, MemoryStatus.ACTIVE.name())
                .last("ORDER BY FIELD(importance,'HIGH','MEDIUM','LOW'), created_at DESC LIMIT " + Math.max(1, limit));
        return memoryMapper.selectList(qw);
    }

    @Override
    @Transactional
    public MemoryVO update(Long id, Long userId, MemoryUpdateRequest req) {
        Memory memory = requireOwned(id, userId);

        // 校验 importance 合法
        MemoryImportance importance = parseImportance(req.getImportance());
        // 校验 status 合法
        parseStatus(req.getStatus());

        memory.setContent(req.getContent());
        memory.setImportance(importance.name());
        memory.setStatus(req.getStatus());
        memoryMapper.updateById(memory);
        return toVO(memory, characterName(memory.getCharacterId()));
    }

    @Override
    @Transactional
    public void delete(Long id, Long userId) {
        requireOwned(id, userId);
        memoryMapper.deleteById(id);
    }

    @Override
    @Transactional
    public void saveExtracted(Long userId, Long characterId, Long conversationId, List<ExtractedMemory> extracted) {
        if (extracted == null || extracted.isEmpty()) {
            return;
        }
        for (ExtractedMemory em : extracted) {
            if (!StringUtils.hasText(em.content())) {
                continue;
            }
            // 同 key 去重: 已有则更新 content + importance + source; 无 key 直接插入
            Memory existing = null;
            if (StringUtils.hasText(em.memoryKey())) {
                existing = memoryMapper.selectOne(Wrappers.<Memory>lambdaQuery()
                        .eq(Memory::getUserId, userId)
                        .eq(Memory::getCharacterId, characterId)
                        .eq(Memory::getMemoryKey, em.memoryKey())
                        .last("LIMIT 1"));
            }
            if (existing != null) {
                existing.setContent(em.content());
                existing.setImportance(em.importance().name());
                existing.setSourceConversationId(conversationId);
                existing.setStatus(MemoryStatus.ACTIVE.name());
                memoryMapper.updateById(existing);
            } else {
                Memory memory = new Memory();
                memory.setUserId(userId);
                memory.setCharacterId(characterId);
                memory.setMemoryKey(em.memoryKey());
                memory.setContent(em.content());
                memory.setImportance(em.importance().name());
                memory.setStatus(MemoryStatus.ACTIVE.name());
                memory.setSourceConversationId(conversationId);
                memoryMapper.insert(memory);
            }
        }
        log.debug("记忆提取落库: userId={}, characterId={}, 条数={}", userId, characterId, extracted.size());
    }

    /** 校验记忆归属: 不存在或越权统一 40400, 不暴露存在性 */
    private Memory requireOwned(Long id, Long userId) {
        Memory memory = memoryMapper.selectById(id);
        if (memory == null || !Objects.equals(memory.getUserId(), userId)) {
            throw new BizException(ResultCode.NOT_FOUND, "记忆不存在或无权访问");
        }
        return memory;
    }

    private MemoryImportance parseImportance(String s) {
        try {
            return MemoryImportance.valueOf(s);
        } catch (Exception e) {
            throw new BizException(ResultCode.PARAM_ERROR, "重要度非法: " + s);
        }
    }

    private MemoryStatus parseStatus(String s) {
        try {
            return MemoryStatus.valueOf(s);
        } catch (Exception e) {
            throw new BizException(ResultCode.PARAM_ERROR, "状态非法: " + s);
        }
    }

    private String characterName(Long characterId) {
        AiCharacter ch = characterService.getById(characterId);
        return ch != null ? ch.getName() : "已删除角色";
    }

    private MemoryVO toVO(Memory m, String characterName) {
        MemoryVO vo = new MemoryVO();
        vo.setId(m.getId());
        vo.setUserId(m.getUserId());
        vo.setCharacterId(m.getCharacterId());
        vo.setCharacterName(characterName);
        vo.setMemoryKey(m.getMemoryKey());
        vo.setContent(m.getContent());
        vo.setImportance(m.getImportance());
        vo.setStatus(m.getStatus());
        vo.setSourceConversationId(m.getSourceConversationId());
        vo.setCreatedAt(m.getCreatedAt());
        vo.setUpdatedAt(m.getUpdatedAt());
        return vo;
    }
}
