<script setup lang="ts">
/**
 * 会话列表单项: 标题 + 最新消息摘要 + 时间
 */
import { computed } from 'vue'
import type { ConversationVO } from '@/types/api'

const props = defineProps<{
  conversation: ConversationVO
  active: boolean
}>()

defineEmits<{
  select: [id: string]
}>()

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
  <button
    type="button"
    class="item w-full text-left"
    :class="{ active }"
    @click="$emit('select', conversation.id)"
  >
    <div class="flex items-center justify-between gap-2">
      <span class="title truncate text-sm">{{ conversation.title }}</span>
      <span class="time shrink-0 text-xs">{{ timeText }}</span>
    </div>
    <p class="preview mt-1 truncate text-xs">
      {{ conversation.lastMessagePreview || '开始新的对话吧' }}
    </p>
  </button>
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

.title {
  color: var(--text-primary);
}

.time {
  color: var(--text-muted);
}

.preview {
  color: var(--text-secondary);
}
</style>
