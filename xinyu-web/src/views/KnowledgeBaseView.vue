<script setup lang="ts">
/**
 * 知识库管理页 (M3 RAG)
 *
 * 用户级知识库 CRUD + 文档上传:
 * - 卡片列表展示所有知识库 (名称 / 文档数 / 切片数)
 * - 创建知识库抽屉
 * - 点击知识库进入详情: 文档列表 + 上传文档 + 删除文档
 * - 文档上传同步处理 (解析→分块→向量化→入库), 进度提示
 *
 * 风格: TRAE 暗黑系, 与 ModelsView / CharactersView 一致
 */
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { knowledgeApi } from '@/api/modules/knowledge'
import { BizError } from '@/utils/BizError'
import type {
  KnowledgeBaseVO,
  KnowledgeBaseCreateDTO,
} from '@/types/api'

const router = useRouter()

const loading = ref(false)
const errorText = ref('')

/** 知识库列表 */
const kbList = ref<KnowledgeBaseVO[]>([])

/** 创建抽屉 */
const createDrawerOpen = ref(false)
const createForm = ref<KnowledgeBaseCreateDTO>({ name: '', description: '' })
const creating = ref(false)

/** 详情抽屉 */
const detailDrawerOpen = ref(false)
const detailKb = ref<KnowledgeBaseVO | null>(null)
const detailLoading = ref(false)

/** 上传状态 */
const uploading = ref(false)
const uploadError = ref('')

onMounted(async () => {
  await loadList()
})

async function loadList(): Promise<void> {
  loading.value = true
  errorText.value = ''
  try {
    kbList.value = await knowledgeApi.list()
  } catch (e) {
    errorText.value = e instanceof BizError ? e.message : '加载失败'
  } finally {
    loading.value = false
  }
}

function openCreate(): void {
  createForm.value = { name: '', description: '' }
  createDrawerOpen.value = true
}

async function submitCreate(): Promise<void> {
  if (!createForm.value.name.trim()) {
    errorText.value = '请填写知识库名称'
    return
  }
  creating.value = true
  try {
    await knowledgeApi.create(createForm.value)
    createDrawerOpen.value = false
    await loadList()
  } catch (e) {
    errorText.value = e instanceof BizError ? e.message : '创建失败'
  } finally {
    creating.value = false
  }
}

async function openDetail(kb: KnowledgeBaseVO): Promise<void> {
  detailKb.value = kb
  detailDrawerOpen.value = true
  uploadError.value = ''
  await loadDetail(kb.id)
}

async function loadDetail(kbId: string): Promise<void> {
  detailLoading.value = true
  try {
    detailKb.value = await knowledgeApi.detail(kbId)
  } catch (e) {
    errorText.value = e instanceof BizError ? e.message : '加载详情失败'
  } finally {
    detailLoading.value = false
  }
}

async function onFileSelected(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement
  if (!input.files || input.files.length === 0 || !detailKb.value) return
  const file = input.files[0]
  uploading.value = true
  uploadError.value = ''
  try {
    await knowledgeApi.uploadDocument(detailKb.value.id, file)
    await loadDetail(detailKb.value.id)
    // 同步刷新列表计数
    await loadList()
  } catch (e) {
    uploadError.value = e instanceof BizError ? e.message : '上传失败, 请重试'
  } finally {
    uploading.value = false
    // 清空 input, 允许重复选同一文件
    input.value = ''
  }
}

async function deleteKb(kbId: string): Promise<void> {
  if (!confirm('删除知识库将同时删除所有文档和向量, 不可恢复, 确认删除?')) return
  try {
    await knowledgeApi.remove(kbId)
    await loadList()
  } catch (e) {
    errorText.value = e instanceof BizError ? e.message : '删除失败'
  }
}

async function deleteDoc(docId: string): Promise<void> {
  if (!detailKb.value) return
  if (!confirm('确认删除此文档及其所有向量?')) return
  try {
    await knowledgeApi.removeDocument(detailKb.value.id, docId)
    await loadDetail(detailKb.value.id)
    await loadList()
  } catch (e) {
    uploadError.value = e instanceof BizError ? e.message : '删除文档失败'
  }
}

function formatSize(bytes: number): string {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / 1024 / 1024).toFixed(1) + ' MB'
}

function statusText(status: string): string {
  return { READY: '就绪', PROCESSING: '处理中', ERROR: '失败', ACTIVE: '正常' }[status] || status
}

function statusClass(status: string): string {
  return {
    READY: 'status-ready',
    ACTIVE: 'status-ready',
    PROCESSING: 'status-processing',
    ERROR: 'status-error',
  }[status] || ''
}
</script>

<template>
  <div class="kb-page">
    <!-- 顶部栏 -->
    <header class="page-header">
      <button class="back-btn" @click="router.push('/chat')">← 返回</button>
      <h1 class="page-title">知识库</h1>
      <button class="primary-btn" @click="openCreate">+ 新建知识库</button>
    </header>

    <!-- 错误提示 -->
    <div v-if="errorText" class="error-banner">{{ errorText }}</div>

    <!-- 加载中 -->
    <div v-if="loading" class="loading">加载中...</div>

    <!-- 空状态 -->
    <div v-else-if="kbList.length === 0" class="empty">
      <div class="empty-icon">📚</div>
      <p>还没有知识库</p>
      <p class="empty-hint">上传文档创建第一个知识库, 开启 RAG 增强聊天</p>
    </div>

    <!-- 知识库卡片列表 -->
    <div v-else class="kb-grid">
      <div v-for="kb in kbList" :key="kb.id" class="kb-card" @click="openDetail(kb)">
        <div class="kb-card-header">
          <h3 class="kb-name">{{ kb.name }}</h3>
          <span class="kb-status" :class="statusClass(kb.status)">{{ statusText(kb.status) }}</span>
        </div>
        <p class="kb-desc">{{ kb.description || '暂无简介' }}</p>
        <div class="kb-stats">
          <span class="stat">📄 {{ kb.docCount }} 文档</span>
          <span class="stat">🧩 {{ kb.chunkCount }} 切片</span>
        </div>
        <button class="delete-btn" @click.stop="deleteKb(kb.id)">删除</button>
      </div>
    </div>

    <!-- 创建知识库抽屉 -->
    <div v-if="createDrawerOpen" class="drawer-mask" @click="createDrawerOpen = false">
      <div class="drawer" @click.stop>
        <div class="drawer-header">
          <h2>新建知识库</h2>
          <button class="close-btn" @click="createDrawerOpen = false">×</button>
        </div>
        <div class="drawer-body">
          <div class="form-item">
            <label>名称</label>
            <input v-model="createForm.name" placeholder="如: 考研408助手" maxlength="50" />
          </div>
          <div class="form-item">
            <label>简介 (可选)</label>
            <textarea v-model="createForm.description" placeholder="知识库用途描述" maxlength="200" rows="3" />
          </div>
          <button class="primary-btn" :disabled="creating" @click="submitCreate">
            {{ creating ? '创建中...' : '创建' }}
          </button>
        </div>
      </div>
    </div>

    <!-- 知识库详情抽屉 -->
    <div v-if="detailDrawerOpen && detailKb" class="drawer-mask" @click="detailDrawerOpen = false">
      <div class="drawer drawer-wide" @click.stop>
        <div class="drawer-header">
          <h2>{{ detailKb.name }}</h2>
          <button class="close-btn" @click="detailDrawerOpen = false">×</button>
        </div>
        <div class="drawer-body">
          <p v-if="detailKb.description" class="detail-desc">{{ detailKb.description }}</p>
          <div class="kb-stats detail-stats">
            <span class="stat">📄 {{ detailKb.docCount }} 文档</span>
            <span class="stat">🧩 {{ detailKb.chunkCount }} 切片</span>
          </div>

          <!-- 上传区域 -->
          <div class="upload-section">
            <label class="upload-btn" :class="{ uploading }">
              <input type="file" accept=".pdf,.md,.markdown,.txt" @change="onFileSelected" :disabled="uploading" />
              {{ uploading ? '处理中 (解析→分块→向量化)...' : '+ 上传文档 (PDF / Markdown / TXT)' }}
            </label>
            <p v-if="uploadError" class="upload-error">{{ uploadError }}</p>
          </div>

          <!-- 文档列表 -->
          <h3 class="doc-list-title">文档列表</h3>
          <div v-if="detailLoading" class="loading">加载中...</div>
          <div v-else-if="!detailKb.documents || detailKb.documents.length === 0" class="empty-doc">
            暂无文档, 上传第一个文档开始使用
          </div>
          <div v-else class="doc-list">
            <div v-for="doc in detailKb.documents" :key="doc.id" class="doc-item">
              <div class="doc-info">
                <div class="doc-name">{{ doc.fileName }}</div>
                <div class="doc-meta">
                  <span>{{ doc.fileType }}</span>
                  <span>{{ formatSize(doc.fileSize) }}</span>
                  <span>{{ doc.chunkCount }} 块</span>
                  <span class="doc-status" :class="statusClass(doc.status)">{{ statusText(doc.status) }}</span>
                </div>
                <div v-if="doc.status === 'ERROR' && doc.errorMsg" class="doc-error">
                  失败原因: {{ doc.errorMsg }}
                </div>
              </div>
              <button class="delete-btn" @click="deleteDoc(doc.id)">删除</button>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.kb-page {
  max-width: 960px;
  margin: 0 auto;
  padding: 24px 20px 60px;
}

.page-header {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 24px;
}

.back-btn {
  background: transparent;
  border: 1px solid var(--border);
  color: var(--text-secondary);
  padding: 6px 12px;
  border-radius: 6px;
  cursor: pointer;
}
.back-btn:hover {
  border-color: var(--text-secondary);
}

.page-title {
  flex: 1;
  font-size: 20px;
  font-weight: 600;
  margin: 0;
}

.primary-btn {
  background: var(--brand-gradient);
  color: #fff;
  border: none;
  padding: 8px 16px;
  border-radius: 6px;
  cursor: pointer;
  font-size: 14px;
}
.primary-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.error-banner {
  background: rgba(239, 68, 68, 0.1);
  border: 1px solid rgba(239, 68, 68, 0.3);
  color: #f87171;
  padding: 10px 14px;
  border-radius: 6px;
  margin-bottom: 16px;
  font-size: 14px;
}

.loading {
  text-align: center;
  color: var(--text-secondary);
  padding: 40px;
}

.empty {
  text-align: center;
  padding: 60px 20px;
  color: var(--text-secondary);
}
.empty-icon {
  font-size: 48px;
  margin-bottom: 12px;
}
.empty-hint {
  font-size: 13px;
  margin-top: 8px;
  opacity: 0.7;
}

.kb-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 16px;
}

.kb-card {
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: 10px;
  padding: 16px;
  cursor: pointer;
  transition: border-color 0.15s;
  position: relative;
}
.kb-card:hover {
  border-color: var(--accent);
}

.kb-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}
.kb-name {
  font-size: 16px;
  font-weight: 600;
  margin: 0;
}
.kb-status {
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 4px;
}
.status-ready {
  background: rgba(34, 197, 94, 0.15);
  color: #4ade80;
}
.status-processing {
  background: rgba(234, 179, 8, 0.15);
  color: #facc15;
}
.status-error {
  background: rgba(239, 68, 68, 0.15);
  color: #f87171;
}

.kb-desc {
  font-size: 13px;
  color: var(--text-secondary);
  margin: 0 0 12px;
  min-height: 18px;
}

.kb-stats {
  display: flex;
  gap: 12px;
  font-size: 13px;
  color: var(--text-secondary);
}

.delete-btn {
  position: absolute;
  top: 12px;
  right: 12px;
  background: transparent;
  border: 1px solid transparent;
  color: var(--text-secondary);
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 4px;
  cursor: pointer;
  opacity: 0;
  transition: opacity 0.15s;
}
.kb-card:hover .delete-btn {
  opacity: 0.7;
}
.delete-btn:hover {
  color: #f87171;
  border-color: rgba(239, 68, 68, 0.3);
  opacity: 1 !important;
}

/* 抽屉 */
.drawer-mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  justify-content: flex-end;
  z-index: 100;
}
.drawer {
  width: 400px;
  background: rgba(12, 16, 32, 0.92);
  backdrop-filter: blur(var(--blur-glass));
  border-left: 1px solid rgba(255, 255, 255, 0.06);
  box-shadow: -20px 0 60px rgba(0, 0, 0, 0.4);
  height: 100%;
  display: flex;
  flex-direction: column;
}
.drawer-wide {
  width: 560px;
}

.drawer-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  border-bottom: 1px solid var(--border);
}
.drawer-header h2 {
  font-size: 16px;
  font-weight: 600;
  margin: 0;
}
.close-btn {
  background: transparent;
  border: none;
  color: var(--text-secondary);
  font-size: 24px;
  cursor: pointer;
  line-height: 1;
}

.drawer-body {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
}

.form-item {
  margin-bottom: 16px;
}
.form-item label {
  display: block;
  font-size: 13px;
  color: var(--text-secondary);
  margin-bottom: 6px;
}
.form-item input,
.form-item textarea {
  width: 100%;
  background: var(--bg-input);
  border: 1px solid var(--border);
  border-radius: 6px;
  padding: 8px 12px;
  color: var(--text-primary);
  font-size: 14px;
  font-family: inherit;
  resize: vertical;
}
.form-item input:focus,
.form-item textarea:focus {
  outline: none;
  border-color: var(--accent);
}

.detail-desc {
  font-size: 14px;
  color: var(--text-secondary);
  margin: 0 0 12px;
}
.detail-stats {
  margin-bottom: 20px;
}

.upload-section {
  margin-bottom: 24px;
}
.upload-btn {
  display: block;
  text-align: center;
  border: 2px dashed var(--border);
  border-radius: 8px;
  padding: 20px;
  cursor: pointer;
  color: var(--text-secondary);
  font-size: 14px;
  transition: border-color 0.15s;
}
.upload-btn:hover {
  border-color: var(--accent);
  color: var(--accent);
}
.upload-btn.uploading {
  opacity: 0.6;
  cursor: not-allowed;
}
.upload-btn input {
  display: none;
}
.upload-error {
  color: #f87171;
  font-size: 13px;
  margin-top: 8px;
}

.doc-list-title {
  font-size: 14px;
  font-weight: 600;
  margin: 0 0 12px;
}
.empty-doc {
  color: var(--text-secondary);
  font-size: 13px;
  padding: 20px;
  text-align: center;
}

.doc-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.doc-item {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: 6px;
  padding: 12px;
}
.doc-info {
  flex: 1;
  min-width: 0;
}
.doc-name {
  font-size: 14px;
  font-weight: 500;
  margin-bottom: 4px;
  word-break: break-all;
}
.doc-meta {
  display: flex;
  gap: 10px;
  font-size: 12px;
  color: var(--text-secondary);
}
.doc-status {
  font-weight: 500;
}
.doc-error {
  font-size: 12px;
  color: #f87171;
  margin-top: 6px;
  word-break: break-all;
}
</style>
