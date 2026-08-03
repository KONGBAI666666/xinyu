package com.xinyu.rag.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xinyu.common.exception.BizException;
import com.xinyu.common.result.ResultCode;
import com.xinyu.rag.entity.KnowledgeBase;
import com.xinyu.rag.entity.KnowledgeDocument;
import com.xinyu.rag.mapper.KnowledgeBaseMapper;
import com.xinyu.rag.mapper.KnowledgeDocumentMapper;
import com.xinyu.rag.qdrant.QdrantService;
import com.xinyu.rag.vo.KnowledgeBaseVO;
import com.xinyu.rag.vo.KnowledgeDocumentVO;
import com.xinyu.rag.vo.KnowledgeBaseCreateRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 知识库服务: 知识库 CRUD + 文档上传处理
 *
 * <p>文档处理流程 (同步, 上传接口内完成):
 * <pre>
 *   MultipartFile
 *     ↓ DocumentParser.parse
 *   纯文本
 *     ↓ ChunkSplitter.split
 *   分块列表
 *     ↓ EmbeddingClient.embed (批量)
 *   向量列表
 *     ↓ QdrantService.upsertChunks
 *   入库 (MySQL 元数据 + Qdrant 向量)
 * </pre>
 *
 * <p>同步处理的原因: 简历够用版, 单文档处理 3~10s 可接受;
 * 若后续扩展大文件, 再改异步 + 轮询状态。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeBaseService {

    private final KnowledgeBaseMapper kbMapper;
    private final KnowledgeDocumentMapper docMapper;
    private final DocumentParser documentParser;
    private final ChunkSplitter chunkSplitter;
    private final EmbeddingClient embeddingClient;
    private final QdrantService qdrantService;

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
        // 1. 删 Qdrant 所有点
        qdrantService.deleteByKb(kbId);
        // 2. 删文档元数据
        docMapper.delete(new LambdaQueryWrapper<KnowledgeDocument>()
                .eq(KnowledgeDocument::getKbId, kbId));
        // 3. 删知识库
        kbMapper.deleteById(kbId);
        log.info("知识库删除: id={}, userId={}", kbId, userId);
    }

    // ==================== 文档上传 ====================

    /**
     * 上传文档到知识库 (同步处理: 解析 → 分块 → 向量化 → 入库)
     *
     * @return 创建的文档元数据
     */
    @Transactional
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
        doc.setFileType(DocumentParser.FileType.fromFileName(file.getOriginalFilename()).name());
        doc.setFileSize(file.getSize());
        doc.setChunkCount(0);
        doc.setStatus("PROCESSING");
        docMapper.insert(doc);

        try {
            // 2. 解析 + 分块
            String text = documentParser.parse(file.getOriginalFilename(), file.getInputStream());
            List<String> chunks = chunkSplitter.split(text);
            if (chunks.isEmpty()) {
                throw new BizException(ResultCode.PARAM_ERROR, "文档内容为空或解析后无有效文本");
            }
            log.info("文档分块完成: docId={}, 块数={}", doc.getId(), chunks.size());

            // 3. 批量向量化
            List<List<Float>> embeddings = embeddingClient.embed(chunks);

            // 4. 存 Qdrant
            qdrantService.upsertChunks(kbId, doc.getId(), chunks, embeddings);

            // 5. 更新文档元数据 (READY)
            doc.setChunkCount(chunks.size());
            doc.setStatus("READY");
            docMapper.updateById(doc);

            // 6. 更新知识库冗余计数
            kb.setDocCount(kb.getDocCount() + 1);
            kb.setChunkCount(kb.getChunkCount() + chunks.size());
            kbMapper.updateById(kb);

            log.info("文档上传成功: docId={}, kbId={}, 块数={}", doc.getId(), kbId, chunks.size());
            return toDocVO(doc);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            // 处理失败 → 标记 ERROR
            doc.setStatus("ERROR");
            doc.setErrorMsg(e.getMessage() != null ? e.getMessage().substring(0, Math.min(e.getMessage().length(), 500)) : "未知错误");
            docMapper.updateById(doc);
            log.error("文档上传失败: docId={}, kbId={}, cause={}", doc.getId(), kbId, e.getMessage(), e);
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
        // 1. 删 Qdrant 点
        qdrantService.deleteByDoc(kbId, docId);
        // 2. 更新知识库计数
        kb.setDocCount(Math.max(0, kb.getDocCount() - 1));
        kb.setChunkCount(Math.max(0, kb.getChunkCount() - doc.getChunkCount()));
        kbMapper.updateById(kb);
        // 3. 删文档元数据
        docMapper.deleteById(docId);
        log.info("文档删除: docId={}, kbId={}", docId, kbId);
    }

    // ==================== 内部工具 ====================

    /** 获取用户拥有的知识库 (权限校验) */
    private KnowledgeBase getOwnedKb(Long kbId, Long userId) {
        KnowledgeBase kb = kbMapper.selectById(kbId);
        if (kb == null || !kb.getUserId().equals(userId)) {
            throw new BizException(ResultCode.NOT_FOUND, "知识库不存在或无权限");
        }
        return kb;
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
