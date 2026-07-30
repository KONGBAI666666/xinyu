<script setup lang="ts">
/**
 * 消息气泡: USER 右侧渐变 / ASSISTANT 左侧毛玻璃
 * M1-4.3 纯文本展示（white-space 保留换行）;
 * Markdown 渲染 / MessageStatus 四态 / 打字机随 M1-4.4 接入
 */
import type { MessageVO } from '@/types/api'

const props = defineProps<{
  message: MessageVO
}>()

const isUser = props.message.messageType === 'USER'
</script>

<template>
  <div class="flex" :class="isUser ? 'justify-end' : 'justify-start'">
    <div class="bubble text-sm" :class="isUser ? 'bubble-user' : 'bubble-assistant'">
      {{ message.content }}
    </div>
  </div>
</template>

<style scoped>
.bubble {
  max-width: 72%;
  padding: 10px 14px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
  border-radius: var(--radius-bubble);
}

.bubble-user {
  background: var(--brand-gradient);
  color: #fff;
  border-bottom-right-radius: var(--radius-bubble-tail);
}

.bubble-assistant {
  background: var(--bg-elevated);
  color: var(--text-primary);
  border-bottom-left-radius: var(--radius-bubble-tail);
}
</style>
