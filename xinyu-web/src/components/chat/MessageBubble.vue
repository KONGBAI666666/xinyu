<script setup lang="ts">
/**
 * 消息气泡: USER 右侧渐变 / ASSISTANT 左侧毛玻璃
 * ASSISTANT 按 MessageStatus 四态展示（契约三 3.2）:
 *   GENERATING 无文本=呼吸点 / 有文本=打字机+光标
 *   FAILED=错误文案  STOPPED=灰字标注
 * 纯文本展示; Markdown/代码高亮统一 M2
 */
import { computed } from 'vue'
import type { MessageVO } from '@/types/api'

const props = defineProps<{
  message: MessageVO
}>()

const isUser = computed(() => props.message.messageType === 'USER')
const generating = computed(() => props.message.status === 'GENERATING')
</script>

<template>
  <div class="flex" :class="isUser ? 'justify-end' : 'justify-start'">
    <div class="bubble text-sm" :class="isUser ? 'bubble-user' : 'bubble-assistant'">
      <!-- 思考中: 尚无文本时的呼吸点 -->
      <span v-if="generating && !message.content" class="dots" aria-label="思考中">
        <i></i><i></i><i></i>
      </span>
      <template v-else>
        <span>{{ message.content }}</span>
        <!-- 打字机光标: 流式追加期间闪烁 -->
        <span v-if="generating" class="cursor"></span>
      </template>

      <p v-if="message.status === 'FAILED'" class="status-note failed">
        生成失败，请稍后重试
      </p>
      <p v-else-if="message.status === 'STOPPED'" class="status-note stopped">已停止生成</p>
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

/* 思考中呼吸点 */
.dots {
  display: inline-flex;
  gap: 4px;
  align-items: center;
}

.dots i {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--text-secondary);
  animation: breathe 1.2s ease-in-out infinite;
}

.dots i:nth-child(2) {
  animation-delay: 0.2s;
}

.dots i:nth-child(3) {
  animation-delay: 0.4s;
}

@keyframes breathe {
  0%,
  100% {
    opacity: 0.25;
  }
  50% {
    opacity: 1;
  }
}

/* 打字机光标 */
.cursor {
  display: inline-block;
  width: 2px;
  height: 1em;
  margin-left: 2px;
  vertical-align: text-bottom;
  background: var(--brand-from);
  animation: blink 0.8s step-end infinite;
}

@keyframes blink {
  50% {
    opacity: 0;
  }
}

.status-note {
  margin-top: 6px;
  font-size: 12px;
}

.status-note.failed {
  color: var(--color-danger);
}

.status-note.stopped {
  color: var(--text-muted);
}
</style>
