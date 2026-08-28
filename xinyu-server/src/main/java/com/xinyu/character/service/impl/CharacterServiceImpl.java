package com.xinyu.character.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xinyu.character.entity.AiCharacter;
import com.xinyu.character.entity.CharacterFavorite;
import com.xinyu.character.enums.CharacterStatus;
import com.xinyu.character.enums.CreatorType;
import com.xinyu.character.enums.SquareSort;
import com.xinyu.character.mapper.AiCharacterMapper;
import com.xinyu.character.mapper.CharacterFavoriteMapper;
import com.xinyu.character.service.CharacterService;
import com.xinyu.character.vo.CharacterSaveRequest;
import com.xinyu.character.vo.CharacterVO;
import com.xinyu.character.vo.SquareQuery;
import com.xinyu.common.exception.BizException;
import com.xinyu.common.result.ResultCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * AI 角色服务实现
 *
 * <p>关键设计:
 * <ul>
 *   <li>可见性: 自己创建的(任意状态) + 官方 PUBLISHED; 越权访问统一 40400 不暴露存在性</li>
 *   <li>状态机: 用户自建角色默认 DRAFT, 可手动发布(PUBLISHED)/下架(OFFLINE);
 *       PENDING 预留 M4 审核, M2.2 不暴露</li>
 *   <li>可聊性: 创建会话时校验, 官方 PUBLISHED 任何人可聊, 用户自建任意状态创建者可聊</li>
 *   <li>广场: 仅 PUBLISHED, 游客可访问; 排序 RECOMMEND/HOT/LATEST</li>
 *   <li>收藏: 幂等 upsert/delete, uk(user_id, character_id) 防重复</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CharacterServiceImpl extends ServiceImpl<AiCharacterMapper, AiCharacter>
        implements CharacterService {

    private final CharacterFavoriteMapper favoriteMapper;

    @Override
    public List<CharacterVO> listVisible(Long userId) {
        LambdaQueryWrapper<AiCharacter> qw = Wrappers.<AiCharacter>lambdaQuery()
                .and(w -> w
                        .eq(AiCharacter::getCreatorType, CreatorType.OFFICIAL.name())
                        .eq(AiCharacter::getStatus, CharacterStatus.PUBLISHED.name()))
                .or(w -> w
                        .eq(userId != null, AiCharacter::getCreatorId, userId))
                .orderByAsc(AiCharacter::getCreatorType)   // OFFICIAL 排前
                .orderByDesc(AiCharacter::getCreatedAt);
        Set<Long> favoritedIds = favoritedIdSet(userId);
        return list(qw).stream().map(c -> toVO(c, userId, favoritedIds)).toList();
    }

    @Override
    public CharacterVO getByIdForUser(Long id, Long userId) {
        AiCharacter character = getById(id);
        if (character == null || !isVisible(character, userId)) {
            return null;
        }
        return toVO(character, userId, favoritedIdSet(userId));
    }

    @Override
    @Transactional
    public CharacterVO create(Long userId, CharacterSaveRequest req) {
        AiCharacter character = new AiCharacter();
        BeanUtils.copyProperties(req, character);
        // intro 列为 NOT NULL 无默认值, 未提供时兜底空串避免插入失败
        if (!StringUtils.hasText(req.getIntro())) {
            character.setIntro("");
        }
        character.setCreatorId(userId);
        character.setCreatorType(CreatorType.USER.name());
        character.setStatus(StringUtils.hasText(req.getStatus())
                ? parseStatus(req.getStatus()).name()
                : CharacterStatus.DRAFT.name());
        character.setChatCount(0);
        character.setFavoriteCount(0);
        save(character);
        log.info("用户创建角色: userId={}, characterId={}, name={}", userId, character.getId(), character.getName());
        return toVO(character, userId, Set.of());
    }

    @Override
    @Transactional
    public CharacterVO update(Long id, Long userId, CharacterSaveRequest req) {
        AiCharacter character = requireOwned(id, userId);
        character.setName(req.getName());
        character.setAvatarUrl(req.getAvatarUrl());
        character.setIntro(req.getIntro());
        character.setSystemPrompt(req.getSystemPrompt());
        character.setGreeting(req.getGreeting());
        character.setTemperature(req.getTemperature());
        character.setMaxTokens(req.getMaxTokens());
        if (StringUtils.hasText(req.getStatus())) {
            character.setStatus(parseStatus(req.getStatus()).name());
        }
        updateById(character);
        return toVO(character, userId, favoritedIdSet(userId));
    }

    @Override
    @Transactional
    public void delete(Long id, Long userId) {
        AiCharacter character = requireOwned(id, userId);
        // 官方角色不允许删除
        if (CreatorType.OFFICIAL.name().equals(character.getCreatorType())) {
            throw new BizException(ResultCode.PARAM_ERROR, "官方角色不可删除");
        }
        removeById(id);
        log.info("用户删除角色: userId={}, characterId={}", userId, id);
    }

    @Override
    @Transactional
    public CharacterVO switchStatus(Long id, Long userId, String status) {
        AiCharacter character = requireOwned(id, userId);
        character.setStatus(parseStatus(status).name());
        updateById(character);
        return toVO(character, userId, favoritedIdSet(userId));
    }

    @Override
    public AiCharacter getChattable(Long id, Long userId) {
        AiCharacter character = getById(id);
        if (character == null) {
            return null;
        }
        // 用户自建角色: 创建者任意状态可聊
        if (CreatorType.USER.name().equals(character.getCreatorType())
                && Objects.equals(character.getCreatorId(), userId)) {
            return character;
        }
        // 官方角色: 仅 PUBLISHED 任何人可聊
        if (CreatorType.OFFICIAL.name().equals(character.getCreatorType())
                && CharacterStatus.PUBLISHED.name().equals(character.getStatus())) {
            return character;
        }
        return null;
    }

    @Override
    public List<CharacterVO> listSquare(Long userId, SquareQuery query) {
        SquareSort sort = parseSort(query.getSort());
        String keyword = StringUtils.hasText(query.getKeyword()) ? query.getKeyword().trim() : null;

        LambdaQueryWrapper<AiCharacter> qw = Wrappers.<AiCharacter>lambdaQuery()
                .eq(AiCharacter::getStatus, CharacterStatus.PUBLISHED.name());
        if (keyword != null) {
            qw.and(w -> w.like(AiCharacter::getName, keyword)
                    .or().like(AiCharacter::getIntro, keyword));
        }
        switch (sort) {
            case RECOMMEND -> qw
                    .orderByAsc(AiCharacter::getCreatorType)   // OFFICIAL 优先
                    .orderByDesc(AiCharacter::getChatCount);
            case HOT -> qw
                    .orderByDesc(AiCharacter::getFavoriteCount)
                    .orderByDesc(AiCharacter::getChatCount);
            case LATEST -> qw.orderByDesc(AiCharacter::getCreatedAt);
        }
        Set<Long> favoritedIds = favoritedIdSet(userId);
        return list(qw).stream().map(c -> toVO(c, userId, favoritedIds)).toList();
    }

    @Override
    public CharacterVO getSquareDetail(Long id, Long userId) {
        AiCharacter character = getById(id);
        if (character == null || !CharacterStatus.PUBLISHED.name().equals(character.getStatus())) {
            return null;
        }
        return toVO(character, userId, favoritedIdSet(userId));
    }

    @Override
    @Transactional
    public void favorite(Long userId, Long characterId) {
        // 幂等: 已存在不重复插入
        Long exist = favoriteMapper.selectCount(Wrappers.<CharacterFavorite>lambdaQuery()
                .eq(CharacterFavorite::getUserId, userId)
                .eq(CharacterFavorite::getCharacterId, characterId));
        if (exist > 0) {
            return;
        }
        CharacterFavorite fav = new CharacterFavorite();
        fav.setUserId(userId);
        fav.setCharacterId(characterId);
        favoriteMapper.insert(fav);
        // 冗余计数 +1（乐观: 不加锁, 偶发不准可接受）
        lambdaUpdate().eq(AiCharacter::getId, characterId)
                .setSql("favorite_count = favorite_count + 1")
                .update();
    }

    @Override
    @Transactional
    public void unfavorite(Long userId, Long characterId) {
        int deleted = favoriteMapper.delete(Wrappers.<CharacterFavorite>lambdaQuery()
                .eq(CharacterFavorite::getUserId, userId)
                .eq(CharacterFavorite::getCharacterId, characterId));
        if (deleted > 0) {
            // 冗余计数 -1, 带下限保护
            lambdaUpdate().eq(AiCharacter::getId, characterId)
                    .setSql("favorite_count = GREATEST(favorite_count - 1, 0)")
                    .update();
        }
    }

    @Override
    public List<CharacterVO> listFavorites(Long userId) {
        // 先查收藏的角色 ID（按收藏时间倒序）
        List<CharacterFavorite> favs = favoriteMapper.selectList(
                Wrappers.<CharacterFavorite>lambdaQuery()
                        .eq(CharacterFavorite::getUserId, userId)
                        .orderByDesc(CharacterFavorite::getCreatedAt));
        if (favs.isEmpty()) {
            return List.of();
        }
        List<Long> ids = favs.stream().map(CharacterFavorite::getCharacterId).toList();
        // 再查角色（只取 PUBLISHED, 避免下架角色出现在收藏列表）
        Map<Long, AiCharacter> characterMap = listByIds(ids).stream()
                .filter(c -> CharacterStatus.PUBLISHED.name().equals(c.getStatus()))
                .collect(Collectors.toMap(AiCharacter::getId, c -> c));
        // 按收藏顺序返回, 跳过已下架/删除的
        return favs.stream()
                .map(f -> characterMap.get(f.getCharacterId()))
                .filter(Objects::nonNull)
                .map(c -> toVO(c, userId, Set.of(c.getId())))  // 收藏列表里 favorited 必为 true
                .toList();
    }

    /** 可见性: 官方 PUBLISHED 任何人可见; 其余仅创建者可见 */
    private boolean isVisible(AiCharacter c, Long userId) {
        if (CreatorType.OFFICIAL.name().equals(c.getCreatorType())
                && CharacterStatus.PUBLISHED.name().equals(c.getStatus())) {
            return true;
        }
        return Objects.equals(c.getCreatorId(), userId);
    }

    /** 校验角色归属: 不存在/非自建/官方统一 40400, 不暴露存在性 */
    private AiCharacter requireOwned(Long id, Long userId) {
        AiCharacter character = getById(id);
        if (character == null || !Objects.equals(character.getCreatorId(), userId)
                || CreatorType.OFFICIAL.name().equals(character.getCreatorType())) {
            throw new BizException(ResultCode.NOT_FOUND, "角色不存在或无权操作");
        }
        return character;
    }

    private CharacterStatus parseStatus(String s) {
        try {
            return CharacterStatus.valueOf(s);
        } catch (Exception e) {
            throw new BizException(ResultCode.PARAM_ERROR, "角色状态非法: " + s);
        }
    }

    private SquareSort parseSort(String s) {
        if (!StringUtils.hasText(s)) {
            return SquareSort.RECOMMEND;
        }
        try {
            return SquareSort.valueOf(s.toUpperCase());
        } catch (Exception e) {
            throw new BizException(ResultCode.PARAM_ERROR, "排序方式非法: " + s);
        }
    }

    /** 查当前用户已收藏的角色 ID 集合, 用于 VO favorited 字段一次性填充, 避免 N+1 */
    private Set<Long> favoritedIdSet(Long userId) {
        if (userId == null) {
            return Set.of();
        }
        return favoriteMapper.selectList(Wrappers.<CharacterFavorite>lambdaQuery()
                        .eq(CharacterFavorite::getUserId, userId)
                        .select(CharacterFavorite::getCharacterId))
                .stream()
                .map(CharacterFavorite::getCharacterId)
                .collect(Collectors.toSet());
    }

    private CharacterVO toVO(AiCharacter c, Long currentUserId, Set<Long> favoritedIds) {
        CharacterVO vo = new CharacterVO();
        BeanUtils.copyProperties(c, vo);
        vo.setMine(currentUserId != null && Objects.equals(c.getCreatorId(), currentUserId)
                && CreatorType.USER.name().equals(c.getCreatorType()));
        vo.setFavorited(favoritedIds.contains(c.getId()));
        return vo;
    }
}
