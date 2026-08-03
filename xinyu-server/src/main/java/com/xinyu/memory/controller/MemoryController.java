package com.xinyu.memory.controller;

import com.xinyu.common.result.Result;
import com.xinyu.common.security.UserContext;
import com.xinyu.memory.service.MemoryService;
import com.xinyu.memory.vo.MemoryUpdateRequest;
import com.xinyu.memory.vo.MemoryVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 长期记忆接口（用户视角: 列表 / 编辑 / 删除）
 *
 * <p>不暴露新增接口（POST）: 记忆由服务端在每轮对话后异步提取, 用户只能管理已有记忆。
 *
 * <p>数据按用户隔离, 通过 characterId 查询参数筛选角色。
 */
@RestController
@RequestMapping("/api/memories")
public class MemoryController {

    private final MemoryService memoryService;

    public MemoryController(MemoryService memoryService) {
        this.memoryService = memoryService;
    }

    /**
     * 列出当前用户的记忆, 可按角色筛选
     *
     * @param characterId 角色 ID, 不传则返回全部角色的记忆
     */
    @GetMapping
    public Result<List<MemoryVO>> list(@RequestParam(required = false) Long characterId) {
        return Result.success(memoryService.listByUser(UserContext.getUserId(), characterId));
    }

    /** 编辑记忆（content / importance / status） */
    @PutMapping("/{id}")
    public Result<MemoryVO> update(@PathVariable Long id, @Valid @RequestBody MemoryUpdateRequest req) {
        return Result.success(memoryService.update(id, UserContext.getUserId(), req));
    }

    /** 删除记忆 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        memoryService.delete(id, UserContext.getUserId());
        return Result.success(null);
    }
}
