<script setup lang="ts">
/**
 * 会话列表单项: 标题 + 最新消息摘要 + 时间
 * 支持内联重命名（悬停显示编辑图标，点击进入编辑态）
 */
import { computed, nextTick, ref } from 'vue'
import type { ConversationVO } from '@/types/api'

const props = defineProps<{
  conversation: ConversationVO
  active: boolean
}>()

const emit = defineEmits<{
  select: [id: string]
  delete: [id: string]
  rename: [id: string, title: string]
}>()

// ——— 重命名编辑状态 ———
const editing = ref(false)
const editTitle = ref('')
const editInput = ref<HTMLInputElement | null>(null)

function startEdit() {
  editTitle.value = props.conversation.title
  editing.value = true
  nextTick(() => {
    editInput.value?.focus()
    editInput.value?.select()
  })
}

function cancelEdit() {
  editing.value = false
}

function confirmEdit() {
  const trimmed = editTitle.value.trim()
  if (!trimmed) {
    // 空标题不允许, 直接退出编辑态 (恢复原标题)
    editing.value = false
    return
  }
  if (trimmed === props.conversation.title) {
    // 没变化, 直接退出
    editing.value = false
    return
  }
  editing.value = false
  emit('rename', props.conversation.id, trimmed)
}

function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter') {
    e.preventDefault()
    confirmEdit()
  } else if (e.key === 'Escape') {
    e.preventDefault()
    cancelEdit()
  }
}

/** 列表时间: 今天显示 HH:mm, 其它显示 MM-DD */
const timeText = computed(() => {
  const raw = props.conversation.lastMessageAt ?? props.conversation.createdAt
  const date = new Date(raw)
  if (Number.isNaN(date.getTime())) return ''
  const now = new Date()
  const sameDay =
    date.getFullYear() === now.getFullYear() &&
    date.getMonth() === now.getMonth() &&
    date.getDate() === now.getDate()
  const pad = (n: number) => String(n).padStart(2, '0')
  return sameDay
    ? `${pad(date.getHours())}:${pad(date.getMinutes())}`
    : `${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
})
</script>

<template>
  <div
    class="item w-full text-left"
    :class="{ active }"
    @click="!editing && $emit('select', conversation.id)"
  >
    <div class="flex items-center justify-between gap-2">
      <!-- 标题: 编辑态显示 input, 否则显示文本 -->
      <div class="title-wrap flex-1 min-w-0">
        <input
          v-if="editing"
          ref="editInput"
          v-model="editTitle"
          class="title-input"
          maxlength="50"
          @blur="confirmEdit"
          @keydown="onKeydown"
          @click.stop
        />
        <span v-else class="title truncate text-sm">{{ conversation.title }}</span>
      </div>
      <div class="flex items-center gap-1 shrink-0">
        <span class="time text-xs">{{ timeText }}</span>
        <span
          v-if="!editing"
          class="edit-btn"
          title="重命名"
          @click.stop="startEdit"
        >
          <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
            <path d="M9.5 2L12 4.5L5.5 11H3V8.5L9.5 2Z" stroke="currentColor" stroke-width="1.2" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
        </span>
        <span
          v-if="!editing"
          class="del-btn"
          title="删除会话"
          @click.stop="$emit('delete', conversation.id)"
        >
          <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
            <path d="M3 3.5H11M5.5 3.5V2.5C5.5 2.2 5.7 2 6 2H8C8.3 2 8.5 2.2 8.5 2.5V3.5M4.5 3.5L4.8 11C4.8 11.3 5.1 11.5 5.4 11.5H8.6C8.9 11.5 9.2 11.3 9.2 11L9.5 3.5" stroke="currentColor" stroke-width="1.2" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
        </span>
      </div>
    </div>
    <p class="preview mt-1 truncate text-xs">
      {{ conversation.lastMessagePreview || '开始新的对话吧' }}
    </p>
  </div>
</template>

<style scoped>
.item {
  padding: 10px 12px;
  border: none;
  border-radius: var(--radius-btn);
  background: transparent;
  cursor: pointer;
  transition: background var(--duration-base) var(--ease-base);
}

.item:hover {
  background: var(--bg-hover);
}

.item.active {
  background: var(--bg-elevated);
}

.title-wrap {
  min-width: 0;
}

.title {
  color: var(--text-primary);
}

.title-input {
  width: 100%;
  background: var(--bg-input);
  border: 1px solid var(--color-primary);
  border-radius: 4px;
  padding: 2px 6px;
  font-size: 13px;
  color: var(--text-primary);
  outline: none;
}

.time {
  color: var(--text-muted);
}

.edit-btn,
.del-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  border-radius: 4px;
  color: var(--text-muted);
  cursor: pointer;
  opacity: 0;
  transition: all var(--duration-base) var(--ease-base);
}

.item:hover .edit-btn,
.item:hover .del-btn {
  opacity: 1;
}

.edit-btn:hover {
  color: var(--color-primary);
  background: rgba(78, 154, 245, 0.12);
}

.del-btn:hover {
  color: var(--color-danger);
  background: rgba(248, 113, 113, 0.12);
}

.preview {
  color: var(--text-secondary);
}
</style>
