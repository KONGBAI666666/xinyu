package com.xinyu.rag.controller;

import com.xinyu.common.result.Result;
import com.xinyu.common.security.UserContext;
import com.xinyu.rag.service.KnowledgeBaseService;
import com.xinyu.rag.vo.KnowledgeBaseCreateRequest;
import com.xinyu.rag.vo.KnowledgeBaseVO;
import com.xinyu.rag.vo.KnowledgeDocumentVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 知识库接口 (M3 RAG)
 *
 * <p>用户级知识库: 创建 / 列表 / 详情 / 删除 + 文档上传 / 文档删除。
 * 会话通过 conversation.kb_id 绑定知识库 (见 ConversationController)。
 *
 * <p>数据按用户隔离, 所有接口需登录。
 */
@RestController
@RequestMapping("/api/knowledge-bases")
public class KnowledgeBaseController {

    private final KnowledgeBaseService kbService;

    public KnowledgeBaseController(KnowledgeBaseService kbService) {
        this.kbService = kbService;
    }

    /** 列出当前用户的知识库 */
    @GetMapping
    public Result<List<KnowledgeBaseVO>> list() {
        return Result.success(kbService.listByUser(UserContext.getUserId()));
    }

    /** 创建知识库 */
    @PostMapping
    public Result<KnowledgeBaseVO> create(@Valid @RequestBody KnowledgeBaseCreateRequest req) {
        return Result.success(kbService.create(req, UserContext.getUserId()));
    }

    /** 知识库详情 (含文档列表) */
    @GetMapping("/{kbId}")
    public Result<KnowledgeBaseVO> detail(@PathVariable Long kbId) {
        return Result.success(kbService.getDetail(kbId, UserContext.getUserId()));
    }

    /** 删除知识库 (含所有文档 + Qdrant 向量) */
    @DeleteMapping("/{kbId}")
    public Result<Void> delete(@PathVariable Long kbId) {
        kbService.delete(kbId, UserContext.getUserId());
        return Result.success(null);
    }

    /** 上传文档到知识库 (同步处理: 解析 → 分块 → 向量化 → 入库) */
    @PostMapping("/{kbId}/documents")
    public Result<KnowledgeDocumentVO> uploadDocument(
            @PathVariable Long kbId,
            @RequestParam("file") MultipartFile file) {
        return Result.success(kbService.uploadDocument(kbId, UserContext.getUserId(), file));
    }

    /** 删除文档 (元数据 + Qdrant 向量) */
    @DeleteMapping("/{kbId}/documents/{docId}")
    public Result<Void> deleteDocument(
            @PathVariable Long kbId,
            @PathVariable Long docId) {
        kbService.deleteDocument(kbId, docId, UserContext.getUserId());
        return Result.success(null);
    }
}
