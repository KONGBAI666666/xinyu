<script setup lang="ts">
/**
 * 角色管理页（M2.2 角色 CRUD + Prompt 编辑器）
 *
 * 布局:
 * - 顶栏: 返回 + 标题 + 「+ 新建角色」
 * - 角色卡片网格: 官方角色 + 我创建的角色
 *   - 卡片: 头像 + 名称 + intro + 状态徽章 + 编辑/删除/状态切换
 * - 编辑抽屉: Prompt 可视化编辑器
 *   - 基础: 名称 / 头像URL / 一句话介绍 / 开场白
 *   - Prompt: 大文本框 + 变量提示
 *   - 采样: temperature 滑块 + maxTokens 输入
 *   - 状态: DRAFT/PUBLISHED/OFFLINE 胶囊
 *
 * 风格: TRAE 暗黑系, 与 ModelsView/MemoriesView 一致
 */
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useCharactersStore } from '@/stores/character'
import { charactersApi } from '@/api/modules/character'
import { BizError } from '@/utils/BizError'
import type { CharacterSaveDTO, CharacterStatus, CharacterVO } from '@/types/api'

const router = useRouter()
const store = useCharactersStore()

const loading = ref(false)
const errorText = ref('')

/** 编辑抽屉 */
const editing = ref<CharacterVO | null>(null)
const creating = ref(false)
const form = ref<CharacterSaveDTO>(emptyForm())
const submitting = ref(false)

/** 删除确认 */
const deletingId = ref<string | null>(null)

function emptyForm(): CharacterSaveDTO {
  return {
    name: '',
    avatarUrl: null,
    intro: '',
    systemPrompt: '',
    greeting: '',
    temperature: 0.8,
    maxTokens: 1024,
    status: 'DRAFT',
  }
}

const officialList = computed(() => store.official())
const mineList = computed(() => store.mine())

onMounted(async () => {
  loading.value = true
  try {
    await store.load(true)
  } catch (e) {
    errorText.value = e instanceof BizError ? e.message : '加载失败'
  } finally {
    loading.value = false
  }
})

function openCreate(): void {
  creating.value = true
  editing.value = null
  form.value = emptyForm()
}

function openEdit(c: CharacterVO): void {
  creating.value = false
  editing.value = c
  form.value = {
    name: c.name,
    avatarUrl: c.avatarUrl,
    intro: c.intro ?? '',
    systemPrompt: c.systemPrompt,
    greeting: c.greeting,
    temperature: c.temperature,
    maxTokens: c.maxTokens,
    status: c.status,
  }
}

async function submit(): Promise<void> {
  errorText.value = ''
  if (!form.value.name.trim()) {
    errorText.value = '角色名不能为空'
    return
  }
  if (!form.value.systemPrompt.trim()) {
    errorText.value = '人设 Prompt 不能为空'
    return
  }
  if (!form.value.greeting.trim()) {
    errorText.value = '开场白不能为空'
    return
  }
  submitting.value = true
  try {
    if (creating.value) {
      const created = await charactersApi.create(form.value)
      store.list.push(created)
    } else if (editing.value) {
      const updated = await charactersApi.update(editing.value.id, form.value)
      const idx = store.list.findIndex((c) => c.id === updated.id)
      if (idx >= 0) store.list[idx] = updated
    }
    closeDrawer()
  } catch (e) {
    errorText.value = e instanceof BizError ? e.message : '保存失败'
  } finally {
    submitting.value = false
  }
}

function closeDrawer(): void {
  editing.value = null
  creating.value = false
  errorText.value = ''
}

async function switchStatus(c: CharacterVO, status: 'DRAFT' | 'PUBLISHED' | 'OFFLINE'): Promise<void> {
  try {
    const updated = await charactersApi.switchStatus(c.id, status)
    const idx = store.list.findIndex((x) => x.id === updated.id)
    if (idx >= 0) store.list[idx] = updated
  } catch (e) {
    errorText.value = e instanceof BizError ? e.message : '操作失败'
  }
}

async function confirmDelete(): Promise<void> {
  if (!deletingId.value) return
  try {
    await charactersApi.remove(deletingId.value)
    store.list = store.list.filter((c) => c.id !== deletingId.value)
    deletingId.value = null
  } catch (e) {
    errorText.value = e instanceof BizError ? e.message : '删除失败'
  }
}

/** 与当前角色开聊 */
function startChat(c: CharacterVO): void {
  // 跳到聊天页并带上 characterId, ChatView 会用该 id 创建会话
  router.push({ path: '/chat', query: { new: c.id } })
}

const statusLabel: Record<string, string> = {
  DRAFT: '草稿', PENDING: '审核中', PUBLISHED: '已发布', OFFLINE: '已下架',
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
      <h1 class="page-title">角色管理</h1>
      <button class="new-btn" type="button" @click="openCreate">
        <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
          <path d="M7 2V12M2 7H12" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/>
        </svg>
        <span>新建角色</span>
      </button>
    </header>

    <div class="content">
      <!-- 加载态 -->
      <div v-if="loading" class="state-hint">加载中…</div>

      <template v-else>
        <!-- 官方角色 -->
        <section v-if="officialList.length > 0" class="section">
          <h2 class="section-title">官方角色</h2>
          <div class="card-grid">
            <div v-for="c in officialList" :key="c.id" class="char-card official">
              <div class="card-head">
                <div class="avatar" :style="c.avatarUrl ? `background-image:url(${c.avatarUrl})` : ''">
                  <span v-if="!c.avatarUrl">{{ c.name.charAt(0) }}</span>
                </div>
                <div class="head-info">
                  <div class="name-row">
                    <span class="name">{{ c.name }}</span>
                    <span class="official-tag">官方</span>
                  </div>
                  <span class="intro">{{ c.intro || '暂无介绍' }}</span>
                </div>
              </div>
              <div class="card-actions">
                <button class="action-btn primary" type="button" @click="startChat(c)">开聊</button>
              </div>
            </div>
          </div>
        </section>

        <!-- 我创建的角色 -->
        <section class="section">
          <div class="section-head">
            <h2 class="section-title">我创建的</h2>
            <span v-if="mineList.length > 0" class="count">{{ mineList.length }}</span>
          </div>

          <div v-if="mineList.length === 0" class="empty-card" @click="openCreate">
            <div class="empty-icon">
              <svg width="32" height="32" viewBox="0 0 32 32" fill="none">
                <path d="M16 6V26M6 16H26" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/>
              </svg>
            </div>
            <p class="empty-text">创建你的第一个角色</p>
            <p class="empty-hint">自定义人设、开场白、采样温度</p>
          </div>

          <div v-else class="card-grid">
            <div v-for="c in mineList" :key="c.id" class="char-card">
              <div class="card-head">
                <div class="avatar" :style="c.avatarUrl ? `background-image:url(${c.avatarUrl})` : ''">
                  <span v-if="!c.avatarUrl">{{ c.name.charAt(0) }}</span>
                </div>
                <div class="head-info">
                  <div class="name-row">
                    <span class="name">{{ c.name }}</span>
                    <span class="status-badge" :class="`st-${c.status.toLowerCase()}`">{{ statusLabel[c.status] }}</span>
                  </div>
                  <span class="intro">{{ c.intro || '暂无介绍' }}</span>
                </div>
              </div>

              <div class="card-meta">
                <span>温度 {{ c.temperature }}</span>
                <span>·</span>
                <span>{{ c.maxTokens }} tokens</span>
                <span>·</span>
                <span>{{ c.chatCount }} 次对话</span>
              </div>

              <div class="card-actions">
                <button class="action-btn" type="button" @click="startChat(c)">开聊</button>
                <button
                  v-if="c.status === 'DRAFT'"
                  class="action-btn"
                  type="button"
                  title="发布到广场"
                  @click="switchStatus(c, 'PUBLISHED')"
                >发布</button>
                <button
                  v-else
                  class="action-btn"
                  type="button"
                  title="下架回草稿"
                  @click="switchStatus(c, 'DRAFT')"
                >下架</button>
                <button class="action-btn" type="button" @click="openEdit(c)">编辑</button>
                <button class="action-btn danger" type="button" @click="deletingId = c.id">删除</button>
              </div>
            </div>
          </div>
        </section>
      </template>
    </div>

    <!-- 错误提示 -->
    <p v-if="errorText && !editing && !creating" class="toast">{{ errorText }}</p>

    <!-- 编辑/新建抽屉 -->
    <Teleport to="body">
      <Transition name="fade">
        <div v-if="editing || creating" class="mask" @click="closeDrawer" />
      </Transition>
      <Transition name="slide">
        <aside v-if="editing || creating" class="drawer">
          <header class="drawer-header">
            <h2 class="drawer-title">{{ creating ? '新建角色' : '编辑角色' }}</h2>
            <button class="close-btn" type="button" @click="closeDrawer">
              <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
                <path d="M1 1L13 13M13 1L1 13" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/>
              </svg>
            </button>
          </header>

          <div class="drawer-body">
            <!-- 基础信息 -->
            <div class="form-section">
              <h3 class="form-section-title">基础信息</h3>

              <div class="form-group">
                <label class="form-label">角色名 *</label>
                <input v-model="form.name" class="form-input" placeholder="如: 屿屿" />
              </div>

              <div class="form-group">
                <label class="form-label">头像 URL</label>
                <input v-model="form.avatarUrl" class="form-input" placeholder="留空用首字母兜底" />
              </div>

              <div class="form-group">
                <label class="form-label">一句话介绍</label>
                <input v-model="form.intro" class="form-input" placeholder="广场卡片展示, 30 字以内" />
              </div>

              <div class="form-group">
                <label class="form-label">开场白 *</label>
                <textarea v-model="form.greeting" class="form-textarea" rows="2" placeholder="新会话首条 AI 消息, 如: 你好呀, 我是…" />
              </div>
            </div>

            <!-- Prompt 编辑器 -->
            <div class="form-section">
              <h3 class="form-section-title">
                人设 Prompt
                <span class="form-hint">作为 system 消息注入</span>
              </h3>
              <textarea
                v-model="form.systemPrompt"
                class="form-textarea prompt-area"
                rows="10"
                placeholder="定义角色的人设、性格、说话风格、边界。&#10;例: 你是屿屿, 一座温暖的小岛。性格温柔耐心, 像老朋友。说话简洁口语化, 多倾听多共情。"
              />
              <div class="prompt-tips">
                <span class="tip-label">建议结构:</span>
                <span class="tip-text">身份 → 性格 → 说话风格 → 边界 → 语言</span>
              </div>
            </div>

            <!-- 采样参数 -->
            <div class="form-section">
              <h3 class="form-section-title">采样参数</h3>

              <div class="form-group">
                <label class="form-label">
                  温度 {{ form.temperature.toFixed(2) }}
                  <span class="form-hint">0 严谨 / 1 平衡 / 2 发散</span>
                </label>
                <input
                  v-model.number="form.temperature"
                  type="range"
                  min="0"
                  max="2"
                  step="0.1"
                  class="form-slider"
                />
              </div>

              <div class="form-group">
                <label class="form-label">maxTokens</label>
                <input v-model.number="form.maxTokens" type="number" min="1" max="8192" class="form-input" />
              </div>
            </div>

            <!-- 状态 -->
            <div v-if="!creating" class="form-section">
              <h3 class="form-section-title">状态</h3>
              <div class="radio-group">
                <label
                  v-for="s in (['DRAFT','PUBLISHED','OFFLINE'] as CharacterStatus[])"
                  :key="s"
                  class="radio-pill"
                  :class="{ active: form.status === s }"
                >
                  <input v-model="form.status" type="radio" :value="s" class="radio-input" />
                  <span>{{ statusLabel[s] }}</span>
                </label>
              </div>
              <p class="form-tip">草稿仅自己可见; 发布后广场可见(M2.3); 下架后已有会话仍可聊</p>
            </div>

            <p v-if="errorText" class="form-error">{{ errorText }}</p>
          </div>

          <footer class="drawer-footer">
            <button class="cancel-btn" type="button" @click="closeDrawer">取消</button>
            <button class="submit-btn" type="button" :disabled="submitting" @click="submit">
              {{ submitting ? '保存中…' : (creating ? '创建' : '保存') }}
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
          <h3 class="confirm-title">删除角色</h3>
          <p class="confirm-text">删除后角色不可恢复, 已有会话仍可继续但无法再新建。此操作不可撤销。</p>
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
.back-btn:hover { background: var(--bg-hover); color: var(--text-primary); }
.page-title {
  flex: 1;
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
}
.new-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 14px;
  border: none;
  border-radius: var(--radius-btn);
  background: var(--brand-gradient);
  color: white;
  font-size: 13px;
  cursor: pointer;
  transition: all var(--duration-base) var(--ease-base);
}
.new-btn:hover { filter: brightness(1.1); }

/* ---------- 内容区 ---------- */
.content {
  max-width: 960px;
  margin: 0 auto;
  padding: 24px;
}

.state-hint {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 60px;
  color: var(--text-muted);
  font-size: 13px;
}

/* ---------- 分区 ---------- */
.section { margin-bottom: 32px; }
.section-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
}
.section-title {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  color: var(--text-secondary);
  letter-spacing: 0.02em;
}
.count {
  padding: 1px 8px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.06);
  color: var(--text-muted);
  font-size: 11px;
}

/* ---------- 卡片网格 ---------- */
.card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 12px;
}
.char-card {
  padding: 16px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.06);
  transition: all var(--duration-base) var(--ease-base);
}
.char-card:hover {
  background: rgba(255, 255, 255, 0.05);
  border-color: rgba(255, 255, 255, 0.1);
}

.card-head {
  display: flex;
  gap: 12px;
  margin-bottom: 12px;
}
.avatar {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  background: var(--brand-gradient);
  background-size: cover;
  background-position: center;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 18px;
  font-weight: 600;
  flex-shrink: 0;
}
.head-info {
  flex: 1;
  min-width: 0;
}
.name-row {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 3px;
}
.name {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.official-tag {
  padding: 1px 6px;
  border-radius: 4px;
  background: rgba(94, 234, 212, 0.15);
  color: var(--brand-to);
  font-size: 10px;
  font-weight: 600;
  flex-shrink: 0;
}
.status-badge {
  padding: 1px 6px;
  border-radius: 4px;
  font-size: 10px;
  font-weight: 600;
  flex-shrink: 0;
}
.status-badge.st-draft { background: rgba(255, 255, 255, 0.06); color: var(--text-muted); }
.status-badge.st-published { background: rgba(94, 234, 212, 0.15); color: var(--brand-to); }
.status-badge.st-offline { background: rgba(248, 113, 113, 0.12); color: #fca5a5; }
.status-badge.st-pending { background: rgba(245, 158, 11, 0.15); color: #fcd34d; }

.intro {
  font-size: 12px;
  color: var(--text-muted);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.card-meta {
  display: flex;
  gap: 6px;
  margin-bottom: 12px;
  font-size: 11px;
  color: var(--text-muted);
}

.card-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  padding-top: 10px;
  border-top: 1px solid rgba(255, 255, 255, 0.04);
}
.action-btn {
  padding: 4px 12px;
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: var(--radius-btn);
  background: transparent;
  color: var(--text-secondary);
  font-size: 12px;
  cursor: pointer;
  transition: all var(--duration-base) var(--ease-base);
}
.action-btn:hover { background: var(--bg-hover); color: var(--text-primary); }
.action-btn.primary {
  background: var(--brand-gradient);
  border-color: transparent;
  color: white;
}
.action-btn.primary:hover { filter: brightness(1.1); }
.action-btn.danger { color: #f87171; }
.action-btn.danger:hover { background: rgba(248, 113, 113, 0.1); border-color: rgba(248, 113, 113, 0.3); }

/* ---------- 空卡片 ---------- */
.empty-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px;
  border: 1px dashed rgba(255, 255, 255, 0.1);
  border-radius: 12px;
  color: var(--text-muted);
  cursor: pointer;
  transition: all var(--duration-base) var(--ease-base);
}
.empty-card:hover {
  border-color: rgba(94, 234, 212, 0.3);
  background: rgba(94, 234, 212, 0.03);
}
.empty-icon { color: rgba(139, 124, 246, 0.4); margin-bottom: 10px; }
.empty-text { margin: 0 0 4px; font-size: 14px; color: var(--text-secondary); }
.empty-hint { margin: 0; font-size: 12px; }

/* ---------- 抽屉（复用 ModelsView 样式） ---------- */
.mask {
  position: fixed; inset: 0; z-index: 40;
  background: rgba(0, 0, 0, 0.5);
  backdrop-filter: blur(2px);
}
.drawer {
  position: fixed; top: 0; right: 0; bottom: 0; z-index: 50;
  width: 480px; max-width: 90vw;
  display: flex; flex-direction: column;
  background: rgba(12, 16, 32, 0.92);
  backdrop-filter: blur(var(--blur-glass));
  border-left: 1px solid rgba(255, 255, 255, 0.06);
  box-shadow: -20px 0 60px rgba(0, 0, 0, 0.4);
}
.drawer-header {
  display: flex; align-items: center; justify-content: space-between;
  padding: 20px 24px 16px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
}
.drawer-title { margin: 0; font-size: 16px; font-weight: 600; color: var(--text-primary); }
.close-btn {
  display: flex; align-items: center; justify-content: center;
  width: 28px; height: 28px; border: none; border-radius: 8px;
  background: transparent; color: var(--text-muted); cursor: pointer;
  transition: all var(--duration-base) var(--ease-base);
}
.close-btn:hover { background: var(--bg-hover); color: var(--text-primary); }
.drawer-body { flex: 1; overflow-y: auto; padding: 20px 24px; }

.form-section { margin-bottom: 24px; }
.form-section-title {
  margin: 0 0 12px;
  font-size: 12px;
  font-weight: 600;
  color: var(--brand-to);
  letter-spacing: 0.04em;
  display: flex;
  align-items: center;
  gap: 8px;
}
.form-hint {
  font-size: 11px;
  font-weight: 400;
  color: var(--text-muted);
  letter-spacing: 0;
}

.form-group { margin-bottom: 14px; }
.form-label {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
  font-size: 12px;
  color: var(--text-secondary);
}
.form-input {
  width: 100%;
  padding: 8px 12px;
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 8px;
  background: rgba(0, 0, 0, 0.2);
  color: var(--text-primary);
  font-size: 13px;
  outline: none;
  box-sizing: border-box;
  transition: border-color var(--duration-base) var(--ease-base);
}
.form-input:focus { border-color: rgba(94, 234, 212, 0.4); }
.form-input::placeholder { color: var(--text-muted); }

.form-textarea {
  width: 100%;
  padding: 10px 12px;
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 8px;
  background: rgba(0, 0, 0, 0.2);
  color: var(--text-primary);
  font-size: 13px;
  font-family: inherit;
  outline: none;
  resize: vertical;
  box-sizing: border-box;
  transition: border-color var(--duration-base) var(--ease-base);
}
.form-textarea:focus { border-color: rgba(94, 234, 212, 0.4); }
.form-textarea::placeholder { color: var(--text-muted); }
.prompt-area {
  font-family: 'Menlo', 'Monaco', 'Courier New', monospace;
  font-size: 12px;
  line-height: 1.6;
}

.prompt-tips {
  margin-top: 8px;
  padding: 8px 12px;
  border-radius: 6px;
  background: rgba(139, 124, 246, 0.06);
  border: 1px solid rgba(139, 124, 246, 0.15);
  font-size: 11px;
  display: flex;
  gap: 6px;
}
.tip-label { color: #c4b5fd; font-weight: 600; }
.tip-text { color: var(--text-secondary); }

.form-slider {
  width: 100%;
  accent-color: var(--brand-to);
  cursor: pointer;
}

.form-tip {
  margin: 8px 0 0;
  font-size: 11px;
  color: var(--text-muted);
}

.radio-group { display: flex; gap: 8px; }
.radio-pill {
  display: flex; align-items: center; gap: 6px;
  padding: 6px 14px;
  border: 1px solid rgba(255, 255, 255, 0.08); border-radius: 999px;
  background: transparent; color: var(--text-secondary);
  font-size: 12px; cursor: pointer;
  transition: all var(--duration-base) var(--ease-base);
}
.radio-pill:hover { background: var(--bg-hover); }
.radio-pill.active {
  background: rgba(94, 234, 212, 0.1);
  border-color: rgba(94, 234, 212, 0.3);
  color: var(--brand-to);
}
.radio-input { display: none; }

.form-error {
  margin: 12px 0 0; padding: 8px 12px; border-radius: 6px;
  background: rgba(248, 113, 113, 0.1); border: 1px solid rgba(248, 113, 113, 0.2);
  font-size: 12px; color: #fca5a5;
}

.drawer-footer { display: flex; gap: 10px; padding: 16px 24px; border-top: 1px solid rgba(255, 255, 255, 0.06); }
.cancel-btn {
  flex: 1; padding: 9px;
  border: 1px solid rgba(255, 255, 255, 0.08); border-radius: 8px;
  background: transparent; color: var(--text-secondary);
  font-size: 13px; cursor: pointer;
  transition: all var(--duration-base) var(--ease-base);
}
.cancel-btn:hover { background: var(--bg-hover); }
.submit-btn {
  flex: 1; padding: 9px;
  border: none; border-radius: 8px;
  background: var(--brand-gradient); color: white;
  font-size: 13px; font-weight: 500; cursor: pointer;
  transition: all var(--duration-base) var(--ease-base);
}
.submit-btn:hover:not(:disabled) { filter: brightness(1.1); }
.submit-btn:disabled { opacity: 0.5; cursor: not-allowed; }

/* ---------- 删除确认 ---------- */
.confirm-dialog {
  position: fixed; top: 50%; left: 50%;
  transform: translate(-50%, -50%); z-index: 50;
  width: 320px; padding: 24px; border-radius: 14px;
  background: rgba(20, 24, 40, 0.95);
  backdrop-filter: blur(var(--blur-glass));
  border: 1px solid rgba(255, 255, 255, 0.08);
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.5);
}
.confirm-title { margin: 0 0 8px; font-size: 15px; font-weight: 600; color: var(--text-primary); }
.confirm-text { margin: 0 0 18px; font-size: 13px; color: var(--text-secondary); }
.confirm-actions { display: flex; gap: 10px; }
.delete-confirm-btn {
  flex: 1; padding: 9px; border: none; border-radius: 8px;
  background: #ef4444; color: white;
  font-size: 13px; font-weight: 500; cursor: pointer;
}
.delete-confirm-btn:hover { background: #dc2626; }

/* ---------- Toast ---------- */
.toast {
  position: fixed; bottom: 30px; left: 50%;
  transform: translateX(-50%);
  padding: 10px 20px; border-radius: 8px;
  background: rgba(239, 68, 68, 0.15); border: 1px solid rgba(239, 68, 68, 0.3);
  color: #fca5a5; font-size: 13px; z-index: 60;
}

/* ---------- 动画 ---------- */
.fade-enter-active, .fade-leave-active { transition: opacity var(--duration-base) var(--ease-base); }
.fade-enter-from, .fade-leave-to { opacity: 0; }
.slide-enter-active, .slide-leave-active { transition: transform var(--duration-base) var(--ease-base); }
.slide-enter-from, .slide-leave-to { transform: translateX(100%); }
.pop-enter-active, .pop-leave-active { transition: all var(--duration-base) var(--ease-base); }
.pop-enter-from, .pop-leave-to { opacity: 0; transform: translate(-50%, -50%) scale(0.95); }
</style>
