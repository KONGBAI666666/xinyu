<script setup lang="ts">
/**
 * 模型管理页（M2 多模型切换）
 *
 * 用户级模型库 CRUD:
 * - 卡片列表展示所有已添加模型, 默认模型高亮
 * - 添加/编辑共用表单抽屉
 * - 一键设为默认 / 删除
 *
 * 风格: TRAE 暗黑系, 与 ChatView / UsagePanel 一致
 */
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useModelsStore } from '@/stores/models'
import { modelsApi } from '@/api/modules/models'
import { BizError } from '@/utils/BizError'
import type { AiModelSaveDTO, AiModelVO } from '@/types/api'

const router = useRouter()
const modelsStore = useModelsStore()

const loading = ref(false)
const errorText = ref('')

/** 表单抽屉 */
const drawerOpen = ref(false)
const editingId = ref<string | null>(null)
const submitting = ref(false)
const form = ref<AiModelSaveDTO>(emptyForm())

/** 删除确认 */
const deletingId = ref<string | null>(null)

function emptyForm(): AiModelSaveDTO {
  return { provider: 'QWEN', modelCode: '', displayName: '', baseUrl: '', apiKey: '', isDefault: false }
}

onMounted(async () => {
  loading.value = true
  try {
    await modelsStore.load(true)
  } catch (e) {
    errorText.value = e instanceof BizError ? e.message : '加载失败'
  } finally {
    loading.value = false
  }
})

function openCreate(): void {
  editingId.value = null
  form.value = emptyForm()
  drawerOpen.value = true
}

function openEdit(model: AiModelVO): void {
  editingId.value = model.id
  form.value = {
    provider: model.provider,
    modelCode: model.modelCode,
    displayName: model.displayName,
    baseUrl: model.baseUrl,
    apiKey: '',
    isDefault: model.isDefault === 1,
  }
  drawerOpen.value = true
}

async function submit(): Promise<void> {
  errorText.value = ''
  if (!form.value.modelCode || !form.value.displayName || !form.value.baseUrl) {
    errorText.value = '请完整填写表单'
    return
  }
  // 添加时 apiKey 必填
  if (!editingId.value && !form.value.apiKey) {
    errorText.value = '请填写 API Key'
    return
  }
  submitting.value = true
  try {
    if (editingId.value) {
      await modelsApi.update(editingId.value, form.value)
    } else {
      await modelsApi.create(form.value)
    }
    drawerOpen.value = false
    await modelsStore.load(true)
  } catch (e) {
    errorText.value = e instanceof BizError ? e.message : '保存失败'
  } finally {
    submitting.value = false
  }
}

async function setDefault(id: string): Promise<void> {
  try {
    await modelsApi.setDefault(id)
    await modelsStore.load(true)
  } catch (e) {
    errorText.value = e instanceof BizError ? e.message : '操作失败'
  }
}

async function confirmDelete(): Promise<void> {
  if (!deletingId.value) return
  try {
    await modelsApi.remove(deletingId.value)
    deletingId.value = null
    await modelsStore.load(true)
  } catch (e) {
    errorText.value = e instanceof BizError ? e.message : '删除失败'
  }
}

/** 预设: 点一下自动填好常见模型配置 */
function applyPreset(preset: string): void {
  const presets: Record<string, Partial<AiModelSaveDTO>> = {
    qwen: { provider: 'QWEN', modelCode: 'qwen-plus', displayName: '通义千问 Plus', baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1' },
    deepseek: { provider: 'DEEPSEEK', modelCode: 'deepseek-chat', displayName: 'DeepSeek Chat', baseUrl: 'https://api.deepseek.com/v1' },
    gpt: { provider: 'OPENAI', modelCode: 'gpt-4o', displayName: 'GPT-4o', baseUrl: 'https://api.openai.com/v1' },
    kimi: { provider: 'MOONSHOT', modelCode: 'moonshot-v1-8k', displayName: 'Kimi 8K', baseUrl: 'https://api.moonshot.cn/v1' },
    glm: { provider: 'GLM', modelCode: 'glm-4-flash', displayName: 'GLM-4 Flash', baseUrl: 'https://open.bigmodel.cn/api/paas/v4' },
    ollama: { provider: 'OLLAMA', modelCode: 'qwen2.5:7b', displayName: 'Ollama 本地', baseUrl: 'http://localhost:11434/v1' },
  }
  const p = presets[preset]
  if (p) {
    form.value = { ...form.value, ...p }
  }
}
</script>

<template>
  <div class="page">
    <!-- 顶栏 -->
    <header class="topbar">
      <button class="back-btn" type="button" @click="router.push('/chat')">
        <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
          <path d="M10 12L6 8L10 4" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
      </button>
      <h1 class="page-title">模型管理</h1>
      <button class="add-btn" type="button" @click="openCreate">+ 添加模型</button>
    </header>

    <!-- 加载态 -->
    <div v-if="loading" class="state-hint">加载中…</div>

    <!-- 空状态 -->
    <div v-else-if="modelsStore.list.length === 0" class="empty-state">
      <div class="empty-icon">
        <svg width="48" height="48" viewBox="0 0 48 48" fill="none">
          <rect x="6" y="10" width="36" height="28" rx="4" stroke="currentColor" stroke-width="2"/>
          <path d="M14 22h8M14 28h14" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
        </svg>
      </div>
      <p class="empty-text">还没有添加任何模型</p>
      <p class="empty-hint">添加一个 AI 模型即可开始聊天</p>
      <button class="empty-add-btn" type="button" @click="openCreate">+ 添加第一个模型</button>
    </div>

    <!-- 模型卡片列表 -->
    <div v-else class="model-list">
      <div
        v-for="model in modelsStore.list"
        :key="model.id"
        class="model-card"
        :class="{ 'is-default': model.isDefault === 1 }"
      >
        <div class="card-header">
          <div class="card-title-row">
            <span class="provider-badge">{{ model.provider }}</span>
            <h3 class="display-name">{{ model.displayName }}</h3>
            <span v-if="model.isDefault === 1" class="default-badge">默认</span>
          </div>
          <span class="model-code">{{ model.modelCode }}</span>
        </div>
        <div class="card-body">
          <div class="info-row">
            <span class="info-label">接口地址</span>
            <span class="info-value" :title="model.baseUrl">{{ model.baseUrl }}</span>
          </div>
          <div class="info-row">
            <span class="info-label">API Key</span>
            <span class="info-value">
              <span v-if="model.apiKeyMasked" class="key-set">{{ model.apiKeyMasked }}</span>
              <span v-else class="key-unset">未配置</span>
            </span>
          </div>
        </div>
        <div class="card-actions">
          <button v-if="model.isDefault !== 1" class="action-btn" type="button" @click="setDefault(model.id)">
            设为默认
          </button>
          <button class="action-btn" type="button" @click="openEdit(model)">编辑</button>
          <button class="action-btn danger" type="button" @click="deletingId = model.id">删除</button>
        </div>
      </div>
    </div>

    <!-- 错误提示 -->
    <p v-if="errorText && !drawerOpen" class="toast">{{ errorText }}</p>

    <!-- 添加/编辑抽屉 -->
    <Teleport to="body">
      <Transition name="fade">
        <div v-if="drawerOpen" class="mask" @click="drawerOpen = false" />
      </Transition>
      <Transition name="slide">
        <aside v-if="drawerOpen" class="drawer">
          <header class="drawer-header">
            <h2 class="drawer-title">{{ editingId ? '编辑模型' : '添加模型' }}</h2>
            <button class="close-btn" type="button" @click="drawerOpen = false">
              <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
                <path d="M1 1L13 13M13 1L1 13" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/>
              </svg>
            </button>
          </header>

          <div class="drawer-body">
            <!-- 预设快捷 -->
            <div v-if="!editingId" class="presets">
              <span class="presets-label">快捷预设</span>
              <div class="presets-grid">
                <button type="button" class="preset-btn" @click="applyPreset('qwen')">通义千问</button>
                <button type="button" class="preset-btn" @click="applyPreset('deepseek')">DeepSeek</button>
                <button type="button" class="preset-btn" @click="applyPreset('gpt')">GPT</button>
                <button type="button" class="preset-btn" @click="applyPreset('kimi')">Kimi</button>
                <button type="button" class="preset-btn" @click="applyPreset('glm')">GLM</button>
                <button type="button" class="preset-btn" @click="applyPreset('ollama')">Ollama</button>
              </div>
            </div>

            <div class="form-group">
              <label class="form-label">展示名</label>
              <input v-model="form.displayName" class="form-input" placeholder="如: 我的千问" />
            </div>

            <div class="form-group">
              <label class="form-label">供应商</label>
              <input v-model="form.provider" class="form-input" placeholder="QWEN / OPENAI / DEEPSEEK..." />
            </div>

            <div class="form-group">
              <label class="form-label">模型标识</label>
              <input v-model="form.modelCode" class="form-input" placeholder="如: qwen-plus" />
            </div>

            <div class="form-group">
              <label class="form-label">接口地址</label>
              <input v-model="form.baseUrl" class="form-input" placeholder="OpenAI 兼容地址" />
            </div>

            <div class="form-group">
              <label class="form-label">
                API Key
                <span v-if="editingId" class="form-hint">留空保留原值</span>
              </label>
              <input
                v-model="form.apiKey"
                class="form-input"
                type="password"
                :placeholder="editingId ? '••••••（留空不改）' : '明文输入, 后端加密存储'"
              />
            </div>

            <label class="checkbox-row">
              <input v-model="form.isDefault" type="checkbox" class="checkbox" />
              <span>设为默认模型</span>
            </label>

            <p v-if="errorText" class="form-error">{{ errorText }}</p>
          </div>

          <footer class="drawer-footer">
            <button class="cancel-btn" type="button" @click="drawerOpen = false">取消</button>
            <button class="submit-btn" type="button" :disabled="submitting" @click="submit">
              {{ submitting ? '保存中…' : '保存' }}
            </button>
          </footer>
        </aside>
      </Transition>
    </Teleport>

    <!-- 删除确认 -->
    <Teleport to="body">
      <Transition name="fade">
        <div v-if="deletingId" class="mask" @click="deletingId = null" />
      </Transition>
      <Transition name="pop">
        <div v-if="deletingId" class="confirm-dialog">
          <h3 class="confirm-title">删除模型</h3>
          <p class="confirm-text">确定删除这个模型配置吗？此操作不可撤销。</p>
          <div class="confirm-actions">
            <button class="cancel-btn" type="button" @click="deletingId = null">取消</button>
            <button class="delete-confirm-btn" type="button" @click="confirmDelete">删除</button>
          </div>
        </div>
      </Transition>
    </Teleport>
  </div>
</template>

<style scoped>
.page {
  min-height: 100vh;
  background: var(--bg-base);
  padding-bottom: 40px;
}

/* ---------- 顶栏 ---------- */
.topbar {
  display: flex;
  align-items: center;
  gap: 12px;
  height: 56px;
  padding: 0 24px;
  position: sticky;
  top: 0;
  z-index: 10;
  background: rgba(12, 16, 32, 0.85);
  backdrop-filter: blur(var(--blur-glass));
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
}

.back-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border: none;
  border-radius: var(--radius-btn);
  background: transparent;
  color: var(--text-muted);
  cursor: pointer;
  transition: all var(--duration-base) var(--ease-base);
}
.back-btn:hover {
  background: var(--bg-hover);
  color: var(--text-primary);
}

.page-title {
  flex: 1;
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
}

.add-btn {
  padding: 7px 16px;
  border: none;
  border-radius: var(--radius-btn);
  background: var(--brand-gradient);
  color: white;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all var(--duration-base) var(--ease-base);
}
.add-btn:hover {
  filter: brightness(1.1);
}

/* ---------- 状态提示 ---------- */
.state-hint {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 60px;
  color: var(--text-muted);
  font-size: 13px;
}

/* ---------- 空状态 ---------- */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 80px 24px;
  color: var(--text-muted);
}

.empty-icon {
  color: rgba(255, 255, 255, 0.1);
  margin-bottom: 16px;
}

.empty-text {
  margin: 0 0 4px;
  font-size: 15px;
  color: var(--text-secondary);
}

.empty-hint {
  margin: 0 0 20px;
  font-size: 12px;
}

.empty-add-btn {
  padding: 8px 20px;
  border: none;
  border-radius: var(--radius-btn);
  background: var(--brand-gradient);
  color: white;
  font-size: 13px;
  cursor: pointer;
}

/* ---------- 模型列表 ---------- */
.model-list {
  max-width: 720px;
  margin: 0 auto;
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.model-card {
  padding: 18px 20px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.06);
  transition: all var(--duration-base) var(--ease-base);
}
.model-card:hover {
  background: rgba(255, 255, 255, 0.05);
  border-color: rgba(255, 255, 255, 0.1);
}
.model-card.is-default {
  border-color: rgba(94, 234, 212, 0.3);
  background: linear-gradient(135deg, rgba(139, 124, 246, 0.06), rgba(94, 234, 212, 0.04));
}

.card-header {
  margin-bottom: 14px;
}
.card-title-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 4px;
}
.provider-badge {
  padding: 2px 8px;
  border-radius: 4px;
  background: rgba(255, 255, 255, 0.08);
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 0.05em;
  color: var(--text-secondary);
}
.display-name {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
}
.default-badge {
  padding: 2px 8px;
  border-radius: 4px;
  background: var(--brand-gradient);
  font-size: 10px;
  font-weight: 600;
  color: white;
}
.model-code {
  font-size: 12px;
  color: var(--text-muted);
  font-family: var(--font-mono, monospace);
}

.card-body {
  margin-bottom: 14px;
}
.info-row {
  display: flex;
  align-items: center;
  padding: 4px 0;
}
.info-label {
  width: 80px;
  font-size: 12px;
  color: var(--text-muted);
}
.info-value {
  flex: 1;
  font-size: 12px;
  color: var(--text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.key-set {
  color: var(--brand-to);
}
.key-unset {
  color: var(--text-muted);
  font-style: italic;
}

.card-actions {
  display: flex;
  gap: 8px;
  padding-top: 12px;
  border-top: 1px solid rgba(255, 255, 255, 0.04);
}
.action-btn {
  padding: 5px 14px;
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: var(--radius-btn);
  background: transparent;
  color: var(--text-secondary);
  font-size: 12px;
  cursor: pointer;
  transition: all var(--duration-base) var(--ease-base);
}
.action-btn:hover {
  background: var(--bg-hover);
  color: var(--text-primary);
}
.action-btn.danger {
  color: #f87171;
}
.action-btn.danger:hover {
  background: rgba(248, 113, 113, 0.1);
  border-color: rgba(248, 113, 113, 0.3);
}

/* ---------- 抽屉 ---------- */
.mask {
  position: fixed;
  inset: 0;
  z-index: 40;
  background: rgba(0, 0, 0, 0.5);
  backdrop-filter: blur(2px);
}

.drawer {
  position: fixed;
  top: 0;
  right: 0;
  bottom: 0;
  z-index: 50;
  width: 440px;
  max-width: 90vw;
  display: flex;
  flex-direction: column;
  background: rgba(12, 16, 32, 0.92);
  backdrop-filter: blur(var(--blur-glass));
  border-left: 1px solid rgba(255, 255, 255, 0.06);
  box-shadow: -20px 0 60px rgba(0, 0, 0, 0.4);
}

.drawer-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px 24px 16px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
}
.drawer-title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
}
.close-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: var(--text-muted);
  cursor: pointer;
  transition: all var(--duration-base) var(--ease-base);
}
.close-btn:hover {
  background: var(--bg-hover);
  color: var(--text-primary);
}

.drawer-body {
  flex: 1;
  overflow-y: auto;
  padding: 20px 24px;
}

/* ---------- 预设 ---------- */
.presets {
  margin-bottom: 20px;
  padding: 14px;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.02);
  border: 1px solid rgba(255, 255, 255, 0.04);
}
.presets-label {
  display: block;
  margin-bottom: 10px;
  font-size: 11px;
  color: var(--text-muted);
}
.presets-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.preset-btn {
  padding: 4px 12px;
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 6px;
  background: transparent;
  color: var(--text-secondary);
  font-size: 12px;
  cursor: pointer;
  transition: all var(--duration-base) var(--ease-base);
}
.preset-btn:hover {
  background: var(--bg-hover);
  color: var(--brand-to);
  border-color: rgba(94, 234, 212, 0.3);
}

/* ---------- 表单 ---------- */
.form-group {
  margin-bottom: 16px;
}
.form-label {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
  font-size: 12px;
  color: var(--text-secondary);
}
.form-hint {
  font-size: 11px;
  color: var(--text-muted);
}
.form-input {
  width: 100%;
  padding: 9px 12px;
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 8px;
  background: rgba(0, 0, 0, 0.2);
  color: var(--text-primary);
  font-size: 13px;
  outline: none;
  transition: border-color var(--duration-base) var(--ease-base);
  box-sizing: border-box;
}
.form-input:focus {
  border-color: rgba(94, 234, 212, 0.4);
}
.form-input::placeholder {
  color: var(--text-muted);
}

.checkbox-row {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--text-secondary);
  cursor: pointer;
}
.checkbox {
  width: 14px;
  height: 14px;
  accent-color: var(--brand-to);
}

.form-error {
  margin: 12px 0 0;
  padding: 8px 12px;
  border-radius: 6px;
  background: rgba(248, 113, 113, 0.1);
  border: 1px solid rgba(248, 113, 113, 0.2);
  font-size: 12px;
  color: #fca5a5;
}

/* ---------- 抽屉底栏 ---------- */
.drawer-footer {
  display: flex;
  gap: 10px;
  padding: 16px 24px;
  border-top: 1px solid rgba(255, 255, 255, 0.06);
}
.cancel-btn {
  flex: 1;
  padding: 9px;
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 8px;
  background: transparent;
  color: var(--text-secondary);
  font-size: 13px;
  cursor: pointer;
  transition: all var(--duration-base) var(--ease-base);
}
.cancel-btn:hover {
  background: var(--bg-hover);
}
.submit-btn {
  flex: 1;
  padding: 9px;
  border: none;
  border-radius: 8px;
  background: var(--brand-gradient);
  color: white;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all var(--duration-base) var(--ease-base);
}
.submit-btn:hover:not(:disabled) {
  filter: brightness(1.1);
}
.submit-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

/* ---------- 删除确认 ---------- */
.confirm-dialog {
  position: fixed;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  z-index: 50;
  width: 320px;
  padding: 24px;
  border-radius: 14px;
  background: rgba(20, 24, 40, 0.95);
  backdrop-filter: blur(var(--blur-glass));
  border: 1px solid rgba(255, 255, 255, 0.08);
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.5);
}
.confirm-title {
  margin: 0 0 8px;
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
}
.confirm-text {
  margin: 0 0 18px;
  font-size: 13px;
  color: var(--text-secondary);
}
.confirm-actions {
  display: flex;
  gap: 10px;
}
.delete-confirm-btn {
  flex: 1;
  padding: 9px;
  border: none;
  border-radius: 8px;
  background: #ef4444;
  color: white;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
}
.delete-confirm-btn:hover {
  background: #dc2626;
}

/* ---------- Toast ---------- */
.toast {
  position: fixed;
  bottom: 30px;
  left: 50%;
  transform: translateX(-50%);
  padding: 10px 20px;
  border-radius: 8px;
  background: rgba(239, 68, 68, 0.15);
  border: 1px solid rgba(239, 68, 68, 0.3);
  color: #fca5a5;
  font-size: 13px;
  z-index: 60;
}

/* ---------- 动画 ---------- */
.fade-enter-active, .fade-leave-active { transition: opacity var(--duration-base) var(--ease-base); }
.fade-enter-from, .fade-leave-to { opacity: 0; }
.slide-enter-active, .slide-leave-active { transition: transform var(--duration-base) var(--ease-base); }
.slide-enter-from, .slide-leave-to { transform: translateX(100%); }
.pop-enter-active, .pop-leave-active { transition: all var(--duration-base) var(--ease-base); }
.pop-enter-from, .pop-leave-to { opacity: 0; transform: translate(-50%, -50%) scale(0.95); }
</style>
