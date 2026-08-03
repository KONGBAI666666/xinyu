<script setup lang="ts">
/**
 * 长期记忆管理页（M2.1 Memory）
 *
 * 用户视角: 仅管理（编辑/启停/删除）, 不能手动新增——记忆由服务端在每轮对话后异步提取。
 *
 * 布局:
 * - 顶栏: 返回 + 标题
 * - 角色筛选胶囊: 全部 + 各角色（记忆按 用户×角色 隔离）
 * - 记忆卡片: memoryKey 标签 + 正文 + 重要度徽章 + 启用开关 + 编辑/删除
 * - 空状态: "TA 还在慢慢了解你"
 *
 * 风格: TRAE 暗黑系, 与 ModelsView 一致
 */
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useMemoriesStore } from '@/stores/memory'
import { memoriesApi } from '@/api/modules/memory'
import { BizError } from '@/utils/BizError'
import type { MemoryUpdateDTO, MemoryVO } from '@/types/api'

const router = useRouter()
const store = useMemoriesStore()

const loading = ref(false)
const errorText = ref('')

/** 当前选中的角色筛选, null = 全部 */
const selectedCharacter = ref<string | null>(null)

const filtered = computed<MemoryVO[]>(() => store.byCharacter(selectedCharacter.value))
const characters = computed(() => store.characters())

/** 编辑抽屉 */
const editing = ref<MemoryVO | null>(null)
const form = ref<MemoryUpdateDTO>(emptyForm())
const submitting = ref(false)

/** 删除确认 */
const deletingId = ref<string | null>(null)

function emptyForm(): MemoryUpdateDTO {
  return { content: '', importance: 'MEDIUM', status: 'ACTIVE' }
}

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

function openEdit(m: MemoryVO): void {
  editing.value = m
  form.value = { content: m.content, importance: m.importance, status: m.status }
}

async function submit(): Promise<void> {
  if (!editing.value) return
  errorText.value = ''
  if (!form.value.content.trim()) {
    errorText.value = '记忆内容不能为空'
    return
  }
  submitting.value = true
  try {
    const updated = await memoriesApi.update(editing.value.id, form.value)
    // 本地替换
    const idx = store.list.findIndex((m) => m.id === updated.id)
    if (idx >= 0) store.list[idx] = updated
    editing.value = null
  } catch (e) {
    errorText.value = e instanceof BizError ? e.message : '保存失败'
  } finally {
    submitting.value = false
  }
}

/** 启停切换: 直接调 update, 状态翻转 */
async function toggleStatus(m: MemoryVO): Promise<void> {
  const next = m.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE'
  try {
    const updated = await memoriesApi.update(m.id, {
      content: m.content,
      importance: m.importance,
      status: next,
    })
    const idx = store.list.findIndex((x) => x.id === updated.id)
    if (idx >= 0) store.list[idx] = updated
  } catch (e) {
    errorText.value = e instanceof BizError ? e.message : '操作失败'
  }
}

async function confirmDelete(): Promise<void> {
  if (!deletingId.value) return
  try {
    await memoriesApi.remove(deletingId.value)
    store.list = store.list.filter((m) => m.id !== deletingId.value)
    deletingId.value = null
  } catch (e) {
    errorText.value = e instanceof BizError ? e.message : '删除失败'
  }
}

const importanceLabel: Record<string, string> = { HIGH: '核心', MEDIUM: '中等', LOW: '次要' }
const keyLabel: Record<string, string> = {
  name: '姓名', job: '职业', location: '位置', hobby: '爱好',
  preference: '偏好', personality: '性格', relationship: '关系', goal: '目标', fact: '事实',
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
      <h1 class="page-title">长期记忆</h1>
    </header>

    <!-- 说明条 -->
    <div class="hint-bar">
      记忆由 AI 在每轮对话后自动提取，注入到后续对话的上下文。你只能编辑或删除，不能手动新增。
    </div>

    <!-- 加载态 -->
    <div v-if="loading" class="state-hint">加载中…</div>

    <!-- 空状态 -->
    <div v-else-if="store.list.length === 0" class="empty-state">
      <div class="empty-icon">
        <svg width="48" height="48" viewBox="0 0 48 48" fill="none">
          <path d="M24 6C14 6 8 12 8 20c0 5 2 8 5 11l-1 7 7-3c1.6.4 3.3.6 5 .6 10 0 16-6 16-14S34 6 24 6z" stroke="currentColor" stroke-width="2" stroke-linejoin="round"/>
          <circle cx="17" cy="20" r="1.5" fill="currentColor"/>
          <circle cx="24" cy="20" r="1.5" fill="currentColor"/>
          <circle cx="31" cy="20" r="1.5" fill="currentColor"/>
        </svg>
      </div>
      <p class="empty-text">TA 还在慢慢了解你</p>
      <p class="empty-hint">多聊几句，AI 会自动记住关于你的长期信息</p>
    </div>

    <template v-else>
      <!-- 角色筛选胶囊 -->
      <div class="filter-bar">
        <button
          class="capsule"
          :class="{ active: selectedCharacter === null }"
          type="button"
          @click="selectedCharacter = null"
        >
          全部
        </button>
        <button
          v-for="c in characters"
          :key="c.id"
          class="capsule"
          :class="{ active: selectedCharacter === c.id }"
          type="button"
          @click="selectedCharacter = c.id"
        >
          {{ c.name }}
        </button>
      </div>

      <!-- 记忆卡片 -->
      <div v-if="filtered.length === 0" class="state-hint">该角色还没有记忆</div>
      <div v-else class="memory-list">
        <div
          v-for="m in filtered"
          :key="m.id"
          class="memory-card"
          :class="{ disabled: m.status === 'DISABLED' }"
        >
          <div class="card-head">
            <div class="head-left">
              <span v-if="m.memoryKey" class="key-tag">{{ keyLabel[m.memoryKey] ?? m.memoryKey }}</span>
              <span class="character-name">{{ m.characterName }}</span>
            </div>
            <span class="importance-badge" :class="`imp-${m.importance.toLowerCase()}`">
              {{ importanceLabel[m.importance] }}
            </span>
          </div>

          <p class="memory-content">{{ m.content }}</p>

          <div class="card-foot">
            <div class="foot-left">
              <button
                class="status-toggle"
                type="button"
                :class="{ on: m.status === 'ACTIVE' }"
                @click="toggleStatus(m)"
              >
                <span class="toggle-dot"></span>
                <span>{{ m.status === 'ACTIVE' ? '启用' : '已停用' }}</span>
              </button>
            </div>
            <div class="foot-right">
              <button class="action-btn" type="button" @click="openEdit(m)">编辑</button>
              <button class="action-btn danger" type="button" @click="deletingId = m.id">删除</button>
            </div>
          </div>
        </div>
      </div>
    </template>

    <!-- 错误提示 -->
    <p v-if="errorText && !editing" class="toast">{{ errorText }}</p>

    <!-- 编辑抽屉 -->
    <Teleport to="body">
      <Transition name="fade">
        <div v-if="editing" class="mask" @click="editing = null" />
      </Transition>
      <Transition name="slide">
        <aside v-if="editing" class="drawer">
          <header class="drawer-header">
            <h2 class="drawer-title">编辑记忆</h2>
            <button class="close-btn" type="button" @click="editing = null">
              <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
                <path d="M1 1L13 13M13 1L1 13" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/>
              </svg>
            </button>
          </header>

          <div class="drawer-body">
            <div class="form-group">
              <label class="form-label">记忆内容</label>
              <textarea v-model="form.content" class="form-textarea" rows="4" placeholder="简短陈述, 如: 用户喜欢 Java 编程" />
            </div>

            <div class="form-group">
              <label class="form-label">重要度</label>
              <div class="radio-group">
                <label v-for="imp in ['HIGH','MEDIUM','LOW']" :key="imp" class="radio-pill" :class="{ active: form.importance === imp }">
                  <input v-model="form.importance" type="radio" :value="imp" class="radio-input" />
                  <span>{{ importanceLabel[imp] }}</span>
                </label>
              </div>
            </div>

            <div class="form-group">
              <label class="form-label">状态</label>
              <div class="radio-group">
                <label class="radio-pill" :class="{ active: form.status === 'ACTIVE' }">
                  <input v-model="form.status" type="radio" value="ACTIVE" class="radio-input" />
                  <span>启用</span>
                </label>
                <label class="radio-pill" :class="{ active: form.status === 'DISABLED' }">
                  <input v-model="form.status" type="radio" value="DISABLED" class="radio-input" />
                  <span>停用</span>
                </label>
              </div>
            </div>

            <p v-if="errorText" class="form-error">{{ errorText }}</p>
          </div>

          <footer class="drawer-footer">
            <button class="cancel-btn" type="button" @click="editing = null">取消</button>
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
          <h3 class="confirm-title">删除记忆</h3>
          <p class="confirm-text">删除后该条记忆不再注入对话。此操作不可撤销。</p>
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
.back-btn:hover { background: var(--bg-hover); color: var(--text-primary); }
.page-title {
  flex: 1;
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
}

/* ---------- 说明条 ---------- */
.hint-bar {
  max-width: 720px;
  margin: 16px auto 0;
  padding: 10px 16px;
  border-radius: 8px;
  background: rgba(139, 124, 246, 0.06);
  border: 1px solid rgba(139, 124, 246, 0.15);
  font-size: 12px;
  color: var(--text-secondary);
  line-height: 1.5;
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
.empty-icon { color: rgba(139, 124, 246, 0.25); margin-bottom: 16px; }
.empty-text { margin: 0 0 4px; font-size: 15px; color: var(--text-secondary); }
.empty-hint { margin: 0; font-size: 12px; }

/* ---------- 筛选胶囊 ---------- */
.filter-bar {
  max-width: 720px;
  margin: 20px auto 0;
  padding: 0 24px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.capsule {
  padding: 5px 14px;
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 999px;
  background: transparent;
  color: var(--text-secondary);
  font-size: 12px;
  cursor: pointer;
  transition: all var(--duration-base) var(--ease-base);
}
.capsule:hover { background: var(--bg-hover); color: var(--text-primary); }
.capsule.active {
  background: var(--brand-gradient);
  border-color: transparent;
  color: white;
}

/* ---------- 记忆卡片 ---------- */
.memory-list {
  max-width: 720px;
  margin: 12px auto 0;
  padding: 0 24px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.memory-card {
  padding: 16px 18px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.06);
  transition: all var(--duration-base) var(--ease-base);
}
.memory-card:hover {
  background: rgba(255, 255, 255, 0.05);
  border-color: rgba(255, 255, 255, 0.1);
}
.memory-card.disabled { opacity: 0.55; }

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}
.head-left { display: flex; align-items: center; gap: 8px; }
.key-tag {
  padding: 2px 8px;
  border-radius: 4px;
  background: rgba(139, 124, 246, 0.15);
  color: #c4b5fd;
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 0.03em;
}
.character-name {
  font-size: 11px;
  color: var(--text-muted);
}
.importance-badge {
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 10px;
  font-weight: 600;
}
.importance-badge.imp-high { background: rgba(239, 68, 68, 0.15); color: #fca5a5; }
.importance-badge.imp-medium { background: rgba(245, 158, 11, 0.15); color: #fcd34d; }
.importance-badge.imp-low { background: rgba(255, 255, 255, 0.06); color: var(--text-muted); }

.memory-content {
  margin: 0 0 12px;
  font-size: 14px;
  color: var(--text-primary);
  line-height: 1.6;
}

.card-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-top: 10px;
  border-top: 1px solid rgba(255, 255, 255, 0.04);
}
.foot-right { display: flex; gap: 8px; }

/* 启停开关 */
.status-toggle {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 3px 8px 3px 4px;
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 999px;
  background: transparent;
  color: var(--text-muted);
  font-size: 11px;
  cursor: pointer;
  transition: all var(--duration-base) var(--ease-base);
}
.status-toggle .toggle-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--text-muted);
}
.status-toggle.on {
  color: var(--brand-to);
  border-color: rgba(94, 234, 212, 0.3);
}
.status-toggle.on .toggle-dot {
  background: var(--brand-to);
  box-shadow: 0 0 6px rgba(94, 234, 212, 0.6);
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
.action-btn.danger { color: #f87171; }
.action-btn.danger:hover { background: rgba(248, 113, 113, 0.1); border-color: rgba(248, 113, 113, 0.3); }

/* ---------- 抽屉（复用 ModelsView 样式） ---------- */
.mask {
  position: fixed; inset: 0; z-index: 40;
  background: rgba(0, 0, 0, 0.5);
  backdrop-filter: blur(2px);
}
.drawer {
  position: fixed; top: 0; right: 0; bottom: 0; z-index: 50;
  width: 440px; max-width: 90vw;
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

.form-group { margin-bottom: 18px; }
.form-label {
  display: block; margin-bottom: 8px;
  font-size: 12px; color: var(--text-secondary);
}
.form-textarea {
  width: 100%; padding: 10px 12px;
  border: 1px solid rgba(255, 255, 255, 0.08); border-radius: 8px;
  background: rgba(0, 0, 0, 0.2); color: var(--text-primary);
  font-size: 13px; font-family: inherit; outline: none; resize: vertical;
  box-sizing: border-box;
  transition: border-color var(--duration-base) var(--ease-base);
}
.form-textarea:focus { border-color: rgba(94, 234, 212, 0.4); }
.form-textarea::placeholder { color: var(--text-muted); }

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
