package com.xinyu.character.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xinyu.character.entity.AiCharacter;
import com.xinyu.character.vo.CharacterSaveRequest;
import com.xinyu.character.vo.CharacterVO;
import com.xinyu.character.vo.SquareQuery;

import java.util.List;

/**
 * AI 角色服务: character 模块对外唯一入口
 *
 * <p>M2.2 在 M1-3 只读切片基础上补充 CRUD + 状态机。
 * 用户自建角色与官方角色隔离: 用户只能编辑/删除自己创建的角色。
 */
public interface CharacterService extends IService<AiCharacter> {

    /**
     * 列出用户可见的角色: 自己创建的(任意状态) + 官方 PUBLISHED
     *
     * <p>排序: 官方优先 → 自建按创建时间倒序
     *
     * @param userId 当前用户; null 表示游客(仅官方 PUBLISHED)
     */
    List<CharacterVO> listVisible(Long userId);

    /**
     * 获取角色详情(含权限校验)
     *
     * <p>可见性规则:
     * <ul>
     *   <li>官方 PUBLISHED: 任何人可见</li>
     *   <li>用户自建: 仅创建者可见(任意状态)</li>
     *   <li>官方非 PUBLISHED: 仅创建者可见</li>
     * </ul>
     *
     * @return 不可见或不存在返回 null
     */
    CharacterVO getByIdForUser(Long id, Long userId);

    /**
     * 创建角色
     *
     * <p>默认状态: DRAFT(仅创建者可见); 官方角色由 seed-dev.sql 预置, 不走此接口。
     */
    CharacterVO create(Long userId, CharacterSaveRequest req);

    /**
     * 编辑角色(仅创建者可编辑自己的角色)
     */
    CharacterVO update(Long id, Long userId, CharacterSaveRequest req);

    /**
     * 删除角色(仅创建者可删除自己的角色)
     */
    void delete(Long id, Long userId);

    /**
     * 切换角色状态(DRAFT ↔ PUBLISHED ↔ OFFLINE)
     *
     * @param status 目标状态: DRAFT / PUBLISHED / OFFLINE
     */
    CharacterVO switchStatus(Long id, Long userId, String status);

    /**
     * 校验角色可被用于创建会话
     *
     * <p>规则: 官方 PUBLISHED 任何人可聊; 用户自建角色任意状态创建者均可聊;
     * 其余情况(他人 DRAFT / 官方 OFFLINE 等)不可聊。
     *
     * @return 可用返回角色实体, 不可用返回 null
     */
    AiCharacter getChattable(Long id, Long userId);

    /**
     * 广场列表: 仅 PUBLISHED 角色, 支持搜索 + 三种排序
     *
     * <p>游客可访问(userId 可为 null), favorited 恒为 false。
     *
     * @param userId 当前用户; null 表示游客
     * @param query  查询条件(keyword + sort)
     */
    List<CharacterVO> listSquare(Long userId, SquareQuery query);

    /**
     * 广场详情: 仅 PUBLISHED 角色对所有人可见
     *
     * <p>与 {@link #getByIdForUser} 区别: 此接口只看 PUBLISHED, 不暴露自建 DRAFT;
     * 用于 /api/characters/{id}/detail 游客可访问路径。
     *
     * @return 不存在或非 PUBLISHED 返回 null
     */
    CharacterVO getSquareDetail(Long id, Long userId);

    /** 收藏角色(幂等: 已收藏不重复插入) */
    void favorite(Long userId, Long characterId);

    /** 取消收藏(幂等: 未收藏不报错) */
    void unfavorite(Long userId, Long characterId);

    /** 列出当前用户收藏的角色 */
    List<CharacterVO> listFavorites(Long userId);
}
