package com.xinyu.rag.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.xinyu.common.exception.BizException;
import com.xinyu.common.result.ResultCode;
import com.xinyu.llm.AiServiceClient;
import com.xinyu.llm.LlmClientFactory;
import com.xinyu.llm.LlmModelConfig;
import com.xinyu.rag.entity.KnowledgeBase;
import com.xinyu.rag.entity.KnowledgeDocument;
import com.xinyu.rag.mapper.KnowledgeBaseMapper;
import com.xinyu.rag.mapper.KnowledgeDocumentMapper;
import com.xinyu.rag.vo.KnowledgeBaseVO;
import com.xinyu.rag.vo.KnowledgeDocumentVO;
import com.xinyu.rag.vo.KnowledgeBaseCreateRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 知识库服务: 知识库 CRUD + 文档上传处理
 *
 * <p>重构后 AI 能力 (文档解析/分块/向量化/Qdrant) 委托给 Python xinyu-ai 服务,
 * Java 只负责 MySQL 元数据管理和权限校验。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeBaseService {

    /** 允许入库的文档扩展名 (与 Python DocumentParser 支持的格式一致) */
    private static final Set<String> ALLOWED_DOC_EXTENSIONS = Set.of("PDF", "MD", "MARKDOWN", "TXT", "TEXT");

    private final KnowledgeBaseMapper kbMapper;
    private final KnowledgeDocumentMapper docMapper;
    private final AiServiceClient aiServiceClient;
    private final LlmClientFactory llmClientFactory;

    // ==================== 知识库 CRUD ====================

    /** 列出当前用户的知识库 */
    public List<KnowledgeBaseVO> listByUser(Long userId) {
        List<KnowledgeBase> kbs = kbMapper.selectList(new LambdaQueryWrapper<KnowledgeBase>()
                .eq(KnowledgeBase::getUserId, userId)
                .orderByDesc(KnowledgeBase::getCreatedAt));
        return kbs.stream().map(this::toVO).collect(Collectors.toList());
    }

    /** 获取知识库详情 (含文档列表) */
    public KnowledgeBaseVO getDetail(Long kbId, Long userId) {
        KnowledgeBase kb = getOwnedKb(kbId, userId);
        KnowledgeBaseVO vo = toVO(kb);
        List<KnowledgeDocument> docs = docMapper.selectList(new LambdaQueryWrapper<KnowledgeDocument>()
                .eq(KnowledgeDocument::getKbId, kbId)
                .orderByDesc(KnowledgeDocument::getCreatedAt));
        vo.setDocuments(docs.stream().map(this::toDocVO).collect(Collectors.toList()));
        return vo;
    }

    /** 创建知识库 */
    public KnowledgeBaseVO create(KnowledgeBaseCreateRequest req, Long userId) {
        KnowledgeBase kb = new KnowledgeBase();
        kb.setUserId(userId);
        kb.setName(req.getName());
        kb.setDescription(req.getDescription());
        kb.setDocCount(0);
        kb.setChunkCount(0);
        kb.setStatus("ACTIVE");
        kbMapper.insert(kb);
        log.info("知识库创建: id={}, userId={}, name={}", kb.getId(), userId, kb.getName());
        return toVO(kb);
    }

    /** 删除知识库 (含所有文档 + Qdrant 点) */
    @Transactional
    public void delete(Long kbId, Long userId) {
        KnowledgeBase kb = getOwnedKb(kbId, userId);
        // 1. 删 Qdrant 向量 (调用 Python)
        aiServiceClient.deleteRagVectors(String.valueOf(kbId), null);
        // 2. 删文档元数据
        docMapper.delete(new LambdaQueryWrapper<KnowledgeDocument>()
                .eq(KnowledgeDocument::getKbId, kbId));
        // 3. 删知识库
        kbMapper.deleteById(kbId);
        log.info("知识库删除: id={}, userId={}", kbId, userId);
    }

    // ==================== 文档上传 ====================

    /**
     * 上传文档到知识库 (委托 Python 处理: 解析 → 分块 → 向量化 → 入 Qdrant)
     *
     * <p>注意: 本方法刻意不加 @Transactional —— Python 处理可能耗时数分钟,
     * 长事务会占住数据库连接; 且失败时抛出的 BizException 会连同
     * PROCESSING/ERROR 文档记录一起回滚, 导致"失败原因"无法展示。
     * 每条 SQL 独立提交, 文档状态在失败路径也能落库。
     *
     * @return 创建的文档元数据
     */
    public KnowledgeDocumentVO uploadDocument(Long kbId, Long userId, MultipartFile file) {
        KnowledgeBase kb = getOwnedKb(kbId, userId);
        if (file.isEmpty()) {
            throw new BizException(ResultCode.PARAM_ERROR, "文件为空");
        }

        // 1. 创建文档元数据 (PROCESSING)
        KnowledgeDocument doc = new KnowledgeDocument();
        doc.setKbId(kbId);
        doc.setUserId(userId);
        doc.setFileName(file.getOriginalFilename());
        String fileName = file.getOriginalFilename();
        String ext = fileName != null && fileName.contains(".")
                ? fileName.substring(fileName.lastIndexOf('.') + 1).toUpperCase()
                : "UNKNOWN";
        // 仅允许解析器支持的格式入库, 避免未知文件当乱码文本进库 (前端 accept 不可靠)
        if (!ALLOWED_DOC_EXTENSIONS.contains(ext)) {
            throw new BizException(ResultCode.PARAM_ERROR, "仅支持 PDF / Markdown / TXT 格式文件");
        }
        doc.setFileType(ext);
        doc.setFileSize(file.getSize());
        doc.setChunkCount(0);
        doc.setStatus("PROCESSING");
        docMapper.insert(doc);

        try {
            // 2. 解析模型配置 (用于 Embedding)
            LlmModelConfig modelConfig = resolveEmbeddingModelConfig(userId);

            // 3. 调用 Python 处理文档 (解析 → 分块 → 向量化 → 入 Qdrant)
            //    传入知识库已锁定的向量化配置, 保证同库内向量维度一致
            Map<String, Object> result = aiServiceClient.processDocument(
                    String.valueOf(kbId), String.valueOf(doc.getId()),
                    file.getOriginalFilename(), file.getBytes(), modelConfig,
                    kb.getEmbeddingModel(), kb.getEmbeddingDim());

            int chunkCount = ((Number) result.getOrDefault("chunkCount", 0)).intValue();
            String status = (String) result.getOrDefault("status", "ERROR");
            String errorMsg = (String) result.get("errorMsg");

            if ("READY".equals(status) && chunkCount > 0) {
                // 竞态守卫: 向量化期间文档被并发删除时, 清理刚写入的向量防孤儿数据
                if (docMapper.selectById(doc.getId()) == null) {
                    aiServiceClient.deleteRagVectors(String.valueOf(kbId), String.valueOf(doc.getId()));
                    throw new BizException(ResultCode.PARAM_ERROR, "文档已在处理期间被删除");
                }
                doc.setChunkCount(chunkCount);
                doc.setStatus("READY");
                docMapper.updateById(doc);

                // 计数原子自增 (读-改-写会在并发上传时丢更新)
                kbMapper.update(null, new LambdaUpdateWrapper<KnowledgeBase>()
                        .eq(KnowledgeBase::getId, kbId)
                        .setSql("doc_count = doc_count + 1")
                        .setSql("chunk_count = chunk_count + " + chunkCount));
                // 首次上传锁定向量化配置 (条件更新: 并发时先成功者为准)
                Object embModel = result.get("embeddingModel");
                if (embModel != null) {
                    Object dim = result.get("embeddingDim");
                    kbMapper.update(null, new LambdaUpdateWrapper<KnowledgeBase>()
                            .eq(KnowledgeBase::getId, kbId)
                            .isNull(KnowledgeBase::getEmbeddingModel)
                            .set(KnowledgeBase::getEmbeddingModel, String.valueOf(embModel))
                            .set(KnowledgeBase::getEmbeddingDim, dim != null ? ((Number) dim).intValue() : null));
                }

                log.info("文档上传成功: docId={}, kbId={}, 块数={}", doc.getId(), kbId, chunkCount);
            } else {
                doc.setStatus("ERROR");
                doc.setErrorMsg(errorMsg != null ? errorMsg.substring(0, Math.min(errorMsg.length(), 500)) : "处理失败");
                docMapper.updateById(doc);
                throw new BizException(ResultCode.SYSTEM_ERROR,
                        "文档处理失败: " + (errorMsg != null ? errorMsg : "未知错误"));
            }
            return toDocVO(doc);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            doc.setStatus("ERROR");
            doc.setErrorMsg(e.getMessage() != null ? e.getMessage().substring(0, Math.min(e.getMessage().length(), 500)) : "未知错误");
            docMapper.updateById(doc);
            log.error("文档上传失败: docId={}, kbId={}", doc.getId(), kbId, e);
            throw new BizException(ResultCode.SYSTEM_ERROR, "文档处理失败: " + e.getMessage());
        }
    }

    /** 删除文档 (元数据 + Qdrant 点) */
    @Transactional
    public void deleteDocument(Long kbId, Long docId, Long userId) {
        KnowledgeBase kb = getOwnedKb(kbId, userId);
        KnowledgeDocument doc = docMapper.selectById(docId);
        if (doc == null || !doc.getKbId().equals(kbId)) {
            throw new BizException(ResultCode.NOT_FOUND, "文档不存在");
        }
        // 1. 删 Qdrant 向量 (调用 Python)
        aiServiceClient.deleteRagVectors(String.valueOf(kbId), String.valueOf(docId));
        // 2. 原子更新知识库计数 (GREATEST 防负数, 避免读-改-写丢更新)
        kbMapper.update(null, new LambdaUpdateWrapper<KnowledgeBase>()
                .eq(KnowledgeBase::getId, kbId)
                .setSql("doc_count = GREATEST(0, doc_count - 1)")
                .setSql("chunk_count = GREATEST(0, chunk_count - " + doc.getChunkCount() + ")"));
        // 3. 删文档元数据
        docMapper.deleteById(docId);
        log.info("文档删除: docId={}, kbId={}", docId, kbId);
    }

    // ==================== 内部工具 ====================

    /** 知识库锁定的 Embedding 模型 (聊天时 RAG 检索用; 未上传过文档返回 null) */
    public String getEmbeddingModel(Long kbId) {
        KnowledgeBase kb = kbMapper.selectById(kbId);
        return kb != null ? kb.getEmbeddingModel() : null;
    }

    /** 获取用户拥有的知识库 (权限校验) */
    private KnowledgeBase getOwnedKb(Long kbId, Long userId) {
        KnowledgeBase kb = kbMapper.selectById(kbId);
        if (kb == null || !kb.getUserId().equals(userId)) {
            throw new BizException(ResultCode.NOT_FOUND, "知识库不存在或无权限");
        }
        return kb;
    }

    /**
     * 解析 Embedding 用的模型配置 (用户默认模型的 API Key + Base URL)
     */
    private LlmModelConfig resolveEmbeddingModelConfig(Long userId) {
        LlmModelConfig config = llmClientFactory.resolveDefaultConfig(userId);
        if (config != null) {
            return config;
        }
        if (llmClientFactory.isDevProfile()) {
            return new LlmModelConfig("mock", "mock", "mock");
        }
        throw new BizException(ResultCode.PARAM_ERROR,
                "尚未配置 AI 模型, 请先在「模型管理」添加一个模型 (Embedding 复用该模型的 API Key)");
    }

    private KnowledgeBaseVO toVO(KnowledgeBase kb) {
        KnowledgeBaseVO vo = new KnowledgeBaseVO();
        BeanUtils.copyProperties(kb, vo);
        return vo;
    }

    private KnowledgeDocumentVO toDocVO(KnowledgeDocument doc) {
        KnowledgeDocumentVO vo = new KnowledgeDocumentVO();
        BeanUtils.copyProperties(doc, vo);
        return vo;
    }
}
