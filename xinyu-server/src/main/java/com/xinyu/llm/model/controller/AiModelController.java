package com.xinyu.llm.model.controller;

import com.xinyu.common.result.Result;
import com.xinyu.common.security.UserContext;
import com.xinyu.llm.model.service.AiModelService;
import com.xinyu.llm.model.vo.AiModelSaveRequest;
import com.xinyu.llm.model.vo.AiModelVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * AI 模型配置接口（用户级 CRUD）
 *
 * <p>每个用户管理自己的模型库, 数据按用户隔离。
 */
@RestController
@RequestMapping("/api/models")
public class AiModelController {

    private final AiModelService modelService;

    public AiModelController(AiModelService modelService) {
        this.modelService = modelService;
    }

    /** 列出当前用户的所有模型 */
    @GetMapping
    public Result<List<AiModelVO>> list() {
        return Result.success(modelService.listByUser(UserContext.getUserId()));
    }

    /** 添加模型 */
    @PostMapping
    public Result<AiModelVO> create(@Valid @RequestBody AiModelSaveRequest req) {
        return Result.success(modelService.create(UserContext.getUserId(), req));
    }

    /** 编辑模型 */
    @PutMapping("/{id}")
    public Result<AiModelVO> update(@PathVariable Long id, @Valid @RequestBody AiModelSaveRequest req) {
        return Result.success(modelService.update(id, UserContext.getUserId(), req));
    }

    /** 删除模型 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        modelService.delete(id, UserContext.getUserId());
        return Result.success(null);
    }

    /** 设为默认模型 */
    @PutMapping("/{id}/default")
    public Result<Void> setDefault(@PathVariable Long id) {
        modelService.setDefault(id, UserContext.getUserId());
        return Result.success(null);
    }
}
