package com.xinyu.character.controller;

import com.xinyu.character.service.CharacterService;
import com.xinyu.character.vo.CharacterSaveRequest;
import com.xinyu.character.vo.CharacterVO;
import com.xinyu.character.vo.SquareQuery;
import com.xinyu.common.result.Result;
import com.xinyu.common.security.UserContext;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * AI 角色接口
 *
 * <p>路径分两类:
 * <ul>
 *   <li>管理类(/api/characters CRUD): 需登录, 仅操作自己创建的角色 + 官方 PUBLISHED 可见</li>
 *   <li>广场类(/api/characters/square, /api/characters/{id}/detail): 游客可访问, 仅 PUBLISHED</li>
 * </ul>
 *
 * <p>收藏类(/api/characters/{id}/favorite, /api/characters/favorites): 需登录, 游客引导登录。
 */
@RestController
@RequestMapping("/api/characters")
public class CharacterController {

    private final CharacterService characterService;

    public CharacterController(CharacterService characterService) {
        this.characterService = characterService;
    }

    // ---------- 广场（游客可访问） ----------

    /**
     * 广场列表: 仅 PUBLISHED 角色, 支持搜索 + 三种排序
     *
     * <p>白名单路径, 游客可访问; userId 由 JwtAuthFilter 可选写入(无 token 时为 null)。
     */
    @GetMapping("/square")
    public Result<List<CharacterVO>> square(SquareQuery query) {
        return Result.success(characterService.listSquare(UserContext.getUserId(), query));
    }

    /**
     * 广场详情: 仅 PUBLISHED 角色对所有人可见
     *
     * <p>白名单路径, 游客可访问; 用于角色详情页。
     */
    @GetMapping("/{id}/detail")
    public Result<CharacterVO> squareDetail(@PathVariable Long id) {
        return Result.success(characterService.getSquareDetail(id, UserContext.getUserId()));
    }

    // ---------- 管理类（需登录） ----------

    /** 列出当前用户可见的角色 */
    @GetMapping
    public Result<List<CharacterVO>> list() {
        return Result.success(characterService.listVisible(UserContext.getUserId()));
    }

    /** 角色详情（管理视角: 自建任意状态 + 官方 PUBLISHED） */
    @GetMapping("/{id}")
    public Result<CharacterVO> detail(@PathVariable Long id) {
        return Result.success(characterService.getByIdForUser(id, UserContext.getUserId()));
    }

    /** 创建角色 */
    @PostMapping
    public Result<CharacterVO> create(@Valid @RequestBody CharacterSaveRequest req) {
        return Result.success(characterService.create(UserContext.getUserId(), req));
    }

    /** 编辑角色(仅自建) */
    @PutMapping("/{id}")
    public Result<CharacterVO> update(@PathVariable Long id, @Valid @RequestBody CharacterSaveRequest req) {
        return Result.success(characterService.update(id, UserContext.getUserId(), req));
    }

    /** 删除角色(仅自建, 官方不可删) */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        characterService.delete(id, UserContext.getUserId());
        return Result.success(null);
    }

    /** 切换角色状态(DRAFT / PUBLISHED / OFFLINE) */
    @PutMapping("/{id}/status")
    public Result<CharacterVO> switchStatus(@PathVariable Long id, @RequestParam String status) {
        return Result.success(characterService.switchStatus(id, UserContext.getUserId(), status));
    }

    // ---------- 收藏（需登录） ----------

    /** 收藏角色(幂等) */
    @PostMapping("/{id}/favorite")
    public Result<Void> favorite(@PathVariable Long id) {
        characterService.favorite(UserContext.getUserId(), id);
        return Result.success(null);
    }

    /** 取消收藏(幂等) */
    @DeleteMapping("/{id}/favorite")
    public Result<Void> unfavorite(@PathVariable Long id) {
        characterService.unfavorite(UserContext.getUserId(), id);
        return Result.success(null);
    }

    /** 列出当前用户收藏的角色 */
    @GetMapping("/favorites")
    public Result<List<CharacterVO>> favorites() {
        return Result.success(characterService.listFavorites(UserContext.getUserId()));
    }
}

