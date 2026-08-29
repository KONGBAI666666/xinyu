<script setup lang="ts">
/**
 * 左侧会话列表栏: 品牌区 + 「+ 新聊天」(角色选择 + 知识库绑定) + 会话列表 + 角色管理入口
 * 数据来自 conversation store; 创建/切换的后续编排（加载消息）由 ChatView 负责
 *
 * M3 RAG: 「+ 新聊天」浮层顶部新增知识库下拉, 选角色时把 kbId 一并传给 ChatView
 */
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useConversationStore } from '@/stores/conversation'
import { useCharactersStore } from '@/stores/character'
import { knowledgeApi } from '@/api/modules/knowledge'
import { BizError } from '@/utils/BizError'
import type { KnowledgeBaseVO } from '@/types/api'
import ConversationItem from './ConversationItem.vue'

const router = useRouter()
const conversationStore = useConversationStore()
const charactersStore = useCharactersStore()

defineProps<{
  /** 创建中: 防止重复点击 */
  creating: boolean
}>()

const emit = defineEmits<{
  /** 普通切换: [id]; 新建: ['new:<characterId>', kbId?] */
  select: [id: string, kbId?: string | null]
}>()

/** 待确认删除的会话 ID (非空时显示确认弹窗) */
const deletingId = ref<string | null>(null)
const deleteError = ref('')
const deleting = ref(false)

function requestDelete(id: string): void {
  deletingId.value = id
  deleteError.value = ''
}

function cancelDelete(): void {
  deletingId.value = null
  deleteError.value = ''
}

async function confirmDelete(): Promise<void> {
  if (!deletingId.value || deleting.value) return
  deleting.value = true
  try {
    await conversationStore.remove(deletingId.value)
    deletingId.value = null
  } catch (e) {
    deleteError.value = e instanceof BizError ? e.message : '删除失败，请稍后重试'
    window.setTimeout(() => { deleteError.value = '' }, 3000)
  } finally {
    deleting.value = false
  }
}

/** 角色选择浮层 */
const pickerOpen = ref(false)

/** 知识库列表 (仅 ACTIVE 可绑定) + 当前选择 */
const kbList = ref<KnowledgeBaseVO[]>([])
const selectedKbId = ref<string | null>(null)
const kbDropdownOpen = ref(false)

function selectedKbName(): string {
  if (!selectedKbId.value) return '不绑定'
  return kbList.value.find((kb) => kb.id === selectedKbId.value)?.name ?? '不绑定'
}

onMounted(() => {
  // 预加载角色列表用于「+ 新聊天」选择
  charactersStore.load().catch(() => {})
  // 预加载知识库列表 (仅 ACTIVE 状态可绑定到会话; 与后端 KnowledgeBaseService 写入值一致)
  knowledgeApi
    .list()
    .then((list) => {
      kbList.value = list.filter((kb) => kb.status === 'ACTIVE')
    })
    .catch(() => {})
})

function togglePicker(): void {
  pickerOpen.value = !pickerOpen.value
  if (!pickerOpen.value) {
    // 关闭浮层时重置知识库选择
    selectedKbId.value = null
    kbDropdownOpen.value = false
  }
}

function closePicker(): void {
  pickerOpen.value = false
  selectedKbId.value = null
  kbDropdownOpen.value = false
}

function toggleKbDropdown(e: Event): void {
  e.stopPropagation()
  kbDropdownOpen.value = !kbDropdownOpen.value
}

function selectKb(kbId: string | null): void {
  selectedKbId.value = kbId
  kbDropdownOpen.value = false
}

function pickCharacter(characterId: string): void {
  const kbId = selectedKbId.value
  closePicker()
  emit('select', `new:${characterId}`, kbId)
}

/** 重命名会话 */
const renameError = ref('')

async function handleRename(id: string, title: string): Promise<void> {
  try {
    await conversationStore.updateTitle(id, title)
  } catch (e) {
    renameError.value = e instanceof BizError ? e.message : '重命名失败，请稍后重试'
    window.setTimeout(() => { renameError.value = '' }, 3000)
  }
}
</script>

<template>
  <aside class="sidebar flex h-full flex-col">
    <h1 class="brand px-4 pt-5 text-xl font-bold">心屿</h1>

    <!-- 操作提示 (重命名/删除等错误) -->
    <p v-if="renameError" class="toast mx-3 mt-2 text-xs">{{ renameError }}</p>

    <div class="px-3 pt-4 relative">
      <button
        type="button"
        class="new-btn w-full py-2 text-sm"
        :disabled="creating"
        @click="togglePicker"
      >
        {{ creating ? '创建中…' : '+ 新聊天' }}
      </button>

      <!-- 外部点击遮罩 -->
      <div v-if="pickerOpen" class="picker-mask" @click="closePicker"></div>

      <!-- 角色选择浮层 -->
      <Transition name="picker">
        <div v-if="pickerOpen" class="picker" @click.stop>
          <p class="picker-title">选择角色开聊</p>

          <!-- 知识库选择 (RAG 增强, 仅当存在知识库时展示) -->
          <div v-if="kbList.length > 0" class="kb-section">
            <button type="button" class="kb-btn" @click="toggleKbDropdown">
              <svg class="kb-icon" width="14" height="14" viewBox="0 0 14 14" fill="none">
                <path d="M2 3.5C2 2.7 2.7 2 3.5 2H6V12H3.5C2.7 12 2 11.3 2 10.5V3.5Z" stroke="currentColor" stroke-width="1.2"/>
                <path d="M8 2H10.5C11.3 2 12 2.7 12 3.5V10.5C12 11.3 11.3 12 10.5 12H8V2Z" stroke="currentColor" stroke-width="1.2"/>
              </svg>
              <span class="kb-label">知识库</span>
              <span class="kb-value" :class="{ active: !!selectedKbId }">{{ selectedKbName() }}</span>
              <svg class="kb-chevron" :class="{ open: kbDropdownOpen }" width="10" height="10" viewBox="0 0 12 12" fill="none">
                <path d="M3 4.5L6 7.5L9 4.5" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" stroke-linejoin="round"/>
              </svg>
            </button>

            <!-- 知识库下拉 -->
            <Transition name="picker">
              <div v-if="kbDropdownOpen" class="kb-dropdown" @click.stop>
                <button
                  type="button"
                  class="kb-option"
                  :class="{ selected: !selectedKbId }"
                  @click="selectKb(null)"
                >
                  <span class="kb-option-name">不绑定</span>
                  <svg v-if="!selectedKbId" class="check" width="12" height="12" viewBox="0 0 14 14" fill="none">
                    <path d="M2 7L6 11L12 3" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/>
                  </svg>
                </button>
                <button
                  v-for="kb in kbList"
                  :key="kb.id"
                  type="button"
                  class="kb-option"
                  :class="{ selected: selectedKbId === kb.id }"
                  @click="selectKb(kb.id)"
                >
                  <div class="kb-option-info">
                    <span class="kb-option-name">{{ kb.name }}</span>
                    <span class="kb-option-meta">{{ kb.docCount }} 文档 · {{ kb.chunkCount }} 切片</span>
                  </div>
                  <svg v-if="selectedKbId === kb.id" class="check" width="12" height="12" viewBox="0 0 14 14" fill="none">
                    <path d="M2 7L6 11L12 3" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/>
                  </svg>
                </button>
              </div>
            </Transition>
          </div>

          <!-- 角色列表 -->
          <button
            v-for="c in charactersStore.list"
            :key="c.id"
            type="button"
            class="picker-item"
            @click="pickCharacter(c.id)"
          >
            <div class="pi-avatar" :style="c.avatarUrl ? `background-image:url(${c.avatarUrl})` : ''">
              <span v-if="!c.avatarUrl">{{ c.name.charAt(0) }}</span>
            </div>
            <div class="pi-info">
              <span class="pi-name">{{ c.name }}</span>
              <span class="pi-intro">{{ c.intro || (c.mine ? '我创建的角色' : '官方角色') }}</span>
            </div>
            <span v-if="c.creatorType === 'OFFICIAL'" class="pi-tag">官方</span>
          </button>

          <div class="picker-divider"></div>
          <button class="picker-manage" type="button" @click="router.push('/settings/characters'); closePicker()">
            <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
              <path d="M7 2V12M2 7H12" stroke="currentColor" stroke-width="1.4" stroke-linecap="round"/>
            </svg>
            <span>管理角色</span>
          </button>
        </div>
      </Transition>
    </div>

    <nav class="mt-3 flex-1 overflow-y-auto px-3 pb-4">
      <p v-if="conversationStore.loading" class="hint text-center text-xs">加载中…</p>
      <p v-else-if="conversationStore.list.length === 0" class="hint text-center text-xs">
        还没有会话，点上方开始聊天
      </p>
      <div v-else class="flex flex-col gap-1">
        <ConversationItem
          v-for="item in conversationStore.list"
          :key="item.id"
          :conversation="item"
          :active="item.id === conversationStore.activeId"
          @select="$emit('select', $event)"
          @delete="requestDelete"
          @rename="handleRename"
        />
      </div>
    </nav>

    <!-- 删除确认弹窗 -->
    <Transition name="confirm">
      <div v-if="deletingId" class="confirm-mask" @click="cancelDelete">
        <div class="confirm-dialog" @click.stop>
          <p class="confirm-title">删除会话</p>
          <p class="confirm-desc">删除后无法恢复，确定要删除这个会话吗？</p>
          <p v-if="deleteError" class="confirm-error">{{ deleteError }}</p>
          <div class="confirm-actions">
            <button type="button" class="confirm-cancel" @click="cancelDelete">取消</button>
            <button type="button" class="confirm-ok" :disabled="deleting" @click="confirmDelete">
              {{ deleting ? '删除中…' : '删除' }}
            </button>
          </div>
        </div>
      </div>
    </Transition>
  </aside>
</template>

<style scoped>
.sidebar {
  background: var(--bg-elevated);
  backdrop-filter: blur(var(--blur-glass));
}

.brand {
  background: var(--brand-gradient);
  background-clip: text;
  -webkit-background-clip: text;
  color: transparent;
}

.new-btn {
  border: none;
  border-radius: var(--radius-btn);
  background: var(--brand-gradient);
  color: #fff;
  cursor: pointer;
  transition: opacity var(--duration-base) var(--ease-base);
}

.new-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.hint {
  margin-top: 24px;
  color: var(--text-muted);
}

/* ---------- 角色选择浮层 ---------- */
.picker-mask {
  position: fixed;
  inset: 0;
  z-index: 19;
}

.picker {
  position: absolute;
  top: calc(100% + 6px);
  left: 12px;
  right: 12px;
  z-index: 20;
  padding: 6px;
  border-radius: 12px;
  background: rgba(20, 24, 40, 0.95);
  backdrop-filter: blur(var(--blur-glass));
  border: 1px solid rgba(255, 255, 255, 0.08);
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.4);
  max-height: 60vh;
  overflow-y: auto;
}

.picker-title {
  margin: 4px 8px 6px;
  font-size: 11px;
  color: var(--text-muted);
  letter-spacing: 0.04em;
}

/* ---------- 知识库选择 (RAG) ---------- */
.kb-section {
  position: relative;
  margin: 4px 4px 8px;
}

.kb-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  width: 100%;
  padding: 8px 10px;
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.03);
  color: var(--text-secondary);
  font-size: 12px;
  cursor: pointer;
  transition: all var(--duration-base) var(--ease-base);
}
.kb-btn:hover {
  background: var(--bg-hover);
  border-color: rgba(255, 255, 255, 0.12);
}

.kb-icon {
  color: var(--text-muted);
  flex-shrink: 0;
}

.kb-label {
  color: var(--text-muted);
}

.kb-value {
  flex: 1;
  text-align: right;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--text-muted);
}
.kb-value.active {
  color: var(--brand-to);
}

.kb-chevron {
  color: var(--text-muted);
  transition: transform var(--duration-base) var(--ease-base);
  flex-shrink: 0;
}
.kb-chevron.open {
  transform: rotate(180deg);
}

.kb-dropdown {
  position: absolute;
  top: calc(100% + 4px);
  left: 0;
  right: 0;
  z-index: 21;
  padding: 4px;
  border-radius: 8px;
  background: rgba(15, 18, 32, 0.98);
  backdrop-filter: blur(var(--blur-glass));
  border: 1px solid rgba(255, 255, 255, 0.08);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.5);
  max-height: 240px;
  overflow-y: auto;
}

.kb-option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  width: 100%;
  padding: 8px 10px;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: var(--text-primary);
  font-size: 12px;
  cursor: pointer;
  transition: background var(--duration-base) var(--ease-base);
  text-align: left;
}
.kb-option:hover { background: var(--bg-hover); }
.kb-option.selected { background: rgba(94, 234, 212, 0.08); }

.kb-option-info {
  display: flex;
  flex-direction: column;
  gap: 1px;
  min-width: 0;
  flex: 1;
}
.kb-option-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.kb-option-meta {
  font-size: 10px;
  color: var(--text-muted);
}

.kb-option .check {
  color: var(--brand-to);
  flex-shrink: 0;
}

.picker-item {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 8px 10px;
  border: none;
  border-radius: 8px;
  background: transparent;
  cursor: pointer;
  transition: background var(--duration-base) var(--ease-base);
  text-align: left;
}
.picker-item:hover { background: var(--bg-hover); }

.pi-avatar {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  background: var(--brand-gradient);
  background-size: cover;
  background-position: center;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 14px;
  font-weight: 600;
  flex-shrink: 0;
}

.pi-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 1px;
}
.pi-name {
  font-size: 13px;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.pi-intro {
  font-size: 11px;
  color: var(--text-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pi-tag {
  padding: 1px 6px;
  border-radius: 4px;
  background: rgba(94, 234, 212, 0.15);
  color: var(--brand-to);
  font-size: 10px;
  font-weight: 600;
  flex-shrink: 0;
}

.picker-divider {
  height: 1px;
  margin: 4px 6px;
  background: rgba(255, 255, 255, 0.06);
}

.picker-manage {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 8px 10px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: var(--text-secondary);
  font-size: 12px;
  cursor: pointer;
  transition: all var(--duration-base) var(--ease-base);
}
.picker-manage:hover { background: var(--bg-hover); color: var(--brand-to); }

/* ---------- 动画 ---------- */
.picker-enter-active, .picker-leave-active {
  transition: all var(--duration-base) var(--ease-base);
}
.picker-enter-from, .picker-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}

/* ---------- 删除确认弹窗 ---------- */
.confirm-mask {
  position: fixed;
  inset: 0;
  z-index: 50;
  background: rgba(0, 0, 0, 0.4);
  display: flex;
  align-items: center;
  justify-content: center;
}

.confirm-dialog {
  width: 280px;
  padding: 20px;
  border-radius: 12px;
  background: rgba(20, 24, 40, 0.98);
  border: 1px solid rgba(255, 255, 255, 0.08);
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.5);
}

.confirm-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
}

.confirm-desc {
  margin-top: 8px;
  font-size: 13px;
  color: var(--text-secondary);
  line-height: 1.5;
}

.confirm-error {
  margin-top: 8px;
  font-size: 12px;
  color: var(--color-danger);
}

.toast {
  padding: 6px 10px;
  border-radius: var(--radius-btn);
  background: rgba(248, 113, 113, 0.12);
  color: var(--color-danger);
  text-align: center;
}

.confirm-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 16px;
}

.confirm-cancel {
  padding: 6px 16px;
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 8px;
  background: transparent;
  color: var(--text-secondary);
  font-size: 13px;
  cursor: pointer;
  transition: all var(--duration-base) var(--ease-base);
}
.confirm-cancel:hover {
  background: var(--bg-hover);
  color: var(--text-primary);
}

.confirm-ok {
  padding: 6px 16px;
  border: none;
  border-radius: 8px;
  background: rgba(248, 113, 113, 0.15);
  color: var(--color-danger);
  font-size: 13px;
  cursor: pointer;
  transition: all var(--duration-base) var(--ease-base);
}
.confirm-ok:hover:not(:disabled) {
  background: rgba(248, 113, 113, 0.25);
}
.confirm-ok:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.confirm-enter-active, .confirm-leave-active {
  transition: opacity var(--duration-base) var(--ease-base);
}
.confirm-enter-from, .confirm-leave-to {
  opacity: 0;
}
</style>
