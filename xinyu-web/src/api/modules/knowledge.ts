import { get, post, del, instance } from '@/api/request'
import type {
  KnowledgeBaseVO,
  KnowledgeBaseCreateDTO,
  KnowledgeDocumentVO,
} from '@/types/api'

/**
 * 知识库接口 (M3 RAG)
 *
 * 用户级知识库: 创建 / 列表 / 详情 / 删除 + 文档上传 / 文档删除。
 * 会话通过 kbId 绑定知识库, 实现知识库增强聊天。
 */
export const knowledgeApi = {
  /** 列出当前用户的知识库 */
  list(): Promise<KnowledgeBaseVO[]> {
    return get('/knowledge-bases')
  },

  /** 创建知识库 */
  create(data: KnowledgeBaseCreateDTO): Promise<KnowledgeBaseVO> {
    return post('/knowledge-bases', data)
  },

  /** 知识库详情 (含文档列表) */
  detail(kbId: string): Promise<KnowledgeBaseVO> {
    return get(`/knowledge-bases/${kbId}`)
  },

  /** 删除知识库 (含所有文档 + Qdrant 向量) */
  remove(kbId: string): Promise<void> {
    return del(`/knowledge-bases/${kbId}`)
  },

  /** 上传文档到知识库 (multipart/form-data, 同步处理) */
  uploadDocument(kbId: string, file: File): Promise<KnowledgeDocumentVO> {
    const formData = new FormData()
    formData.append('file', file)
    // 上传处理耗时较长 (解析+分块+向量化), 单独放宽超时到 5 分钟
    return instance.post(`/knowledge-bases/${kbId}/documents`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 300_000,
    }) as Promise<KnowledgeDocumentVO>
  },

  /** 删除文档 (元数据 + Qdrant 向量) */
  removeDocument(kbId: string, docId: string): Promise<void> {
    return del(`/knowledge-bases/${kbId}/documents/${docId}`)
  },
}
