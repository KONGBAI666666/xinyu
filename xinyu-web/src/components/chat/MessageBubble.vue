<script setup lang="ts">
/**
 * 消息气泡: USER 右侧渐变 / ASSISTANT 左侧毛玻璃
 * ASSISTANT 按 MessageStatus 四态展示（契约三 3.2）:
 *   GENERATING 无文本=呼吸点 / 有文本=打字机+光标
 *   FAILED=错误文案  STOPPED=灰字标注
 * M1-6: ASSISTANT 经 markdown 安全渲染管线(marked+DOMPurify+hljs); USER 保持纯文本
 */
import { computed } from 'vue'
import type { MessageVO } from '@/types/api'
import { renderMarkdown } from '@/utils/markdown'

const props = defineProps<{
  message: MessageVO
}>()

const isUser = computed(() => props.message.messageType === 'USER')
const generating = computed(() => props.message.status === 'GENERATING')

/** ASSISTANT 消息的安全 HTML（已消毒, 流式期间随 content 增长重算） */
const renderedContent = computed(() =>
  isUser.value ? '' : renderMarkdown(props.message.content),
)
</script>

<template>
  <div class="flex" :class="isUser ? 'justify-end' : 'justify-start'">
    <div class="bubble text-sm" :class="isUser ? 'bubble-user' : 'bubble-assistant'">
      <!-- 思考中: 尚无文本时的呼吸点 -->
      <span v-if="generating && !message.content" class="dots" aria-label="思考中">
        <i></i><i></i><i></i>
      </span>
      <template v-else>
        <!-- USER 纯文本插值; ASSISTANT 经消毒后的 HTML, 流式光标由 .generating 伪元素追加 -->
        <span v-if="isUser" class="user-text">{{ message.content }}</span>
        <div v-else class="md-body" :class="{ generating }" v-html="renderedContent"></div>
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
  word-break: break-word;
  border-radius: var(--radius-bubble);
}

/* 用户消息保留手动换行（markdown 侧由块级标签自行排版） */
.user-text {
  white-space: pre-wrap;
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

/* 打字机光标: 挂在 markdown 最后一个块级元素末尾（v-html 内无法插真实节点） */
.md-body.generating > :deep(*:last-child)::after {
  content: '';
  display: inline-block;
  width: 2px;
  height: 1em;
  margin-left: 2px;
  vertical-align: text-bottom;
  background: var(--brand-from);
  animation: blink 0.8s step-end infinite;
}

/* 刚进入流式且首个块未生成时兼容（无子元素） */
.md-body.generating:empty::after {
  content: '';
  display: inline-block;
  width: 2px;
  height: 1em;
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
