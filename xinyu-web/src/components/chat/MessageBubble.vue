<script setup lang="ts">
/**
 * 消息气泡: USER 右侧渐变 / ASSISTANT 左侧毛玻璃
 * ASSISTANT 按 MessageStatus 四态展示（契约三 3.2）:
 *   GENERATING 无文本=呼吸点 / 有文本=打字机+光标
 *   FAILED=错误文案  STOPPED=灰字标注
 * M1-6: ASSISTANT 经 markdown 安全渲染管线(marked+DOMPurify+hljs); USER 保持纯文本
 * M4+: ASSISTANT 气泡下挂操作行 — 重新生成版本切换 / 点赞点踩
 */
import { computed, ref } from 'vue'
import type { MessageVO } from '@/types/api'
import { renderMarkdown } from '@/utils/markdown'

const props = defineProps<{
  message: MessageVO
  /** 重新生成版本组的展示信息 (多于 1 个版本时出现切换器) */
  versionIndex?: number
  versionTotal?: number
  /** 是否展示「重新生成」按钮 (仅最后一条回复且非生成中) */
  canRegenerate?: boolean
}>()

const emit = defineEmits<{
  version: [dir: number]
  feedback: [value: 'LIKE' | 'DISLIKE']
  regenerate: []
}>()

const isUser = computed(() => props.message.messageType === 'USER')
const generating = computed(() => props.message.status === 'GENERATING')
/** 引用列表展开态 (默认收起) */
const citationsOpen = ref(false)

/** 工具名 → 中文展示名 */
const TOOL_NAMES: Record<string, string> = {
  get_current_datetime: '查询当前时间',
  calculate: '计算',
  search_history: '检索历史消息',
}
function toolDisplayName(name: string): string {
  return TOOL_NAMES[name] ?? name
}
/** 终态才允许反馈/重发 (生成中/失败中不可点) */
const actionable = computed(
  () => !isUser.value && (props.message.status === 'COMPLETED' || props.message.status === 'STOPPED'),
)

/** ASSISTANT 消息的安全 HTML（已消毒, 流式期间随 content 增长重算） */
const renderedContent = computed(() =>
  isUser.value ? '' : renderMarkdown(props.message.content),
)
</script>

<template>
  <div class="flex" :class="isUser ? 'justify-end' : 'justify-start'">
    <div class="bubble-wrap" :class="isUser ? 'is-user' : 'is-assistant'">
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
          {{ message.errorMessage || '生成失败，请稍后重试' }}
        </p>
        <p v-else-if="message.status === 'STOPPED'" class="status-note stopped">已停止生成</p>
      </div>

      <!-- 工具调用轨迹: agent 执行过程回执 (仅流式本地态) -->
      <ul v-if="!isUser && message.toolCalls?.length" class="tool-trace">
        <li v-for="(tool, i) in message.toolCalls" :key="i" class="tool-item">
          <span class="tool-name">🔧 {{ toolDisplayName(tool.name) }}</span>
          <span class="tool-result">{{ tool.result }}</span>
        </li>
      </ul>

      <!-- RAG 引用溯源: 可折叠的命中片段列表 -->
      <div v-if="!isUser && message.citations?.length" class="citations">
        <button type="button" class="cite-toggle" @click="citationsOpen = !citationsOpen">
          引用来源 · {{ message.citations.length }}
          <span class="cite-caret">{{ citationsOpen ? '▾' : '▸' }}</span>
        </button>
        <ul v-if="citationsOpen" class="cite-list">
          <li v-for="(cite, i) in message.citations" :key="i" class="cite-item">
            <p class="cite-head">
              <span class="cite-name">{{ cite.fileName || '知识库片段' }} · 第{{ cite.chunkIndex + 1 }}块</span>
              <span class="cite-score">{{ Math.round(cite.score * 100) }}%</span>
            </p>
            <p class="cite-snippet">{{ cite.snippet }}</p>
          </li>
        </ul>
      </div>

      <!-- ASSISTANT 操作行: 版本切换 / 反馈 / 重新生成 -->
      <div v-if="!isUser && !generating" class="msg-actions">
        <template v-if="(versionTotal ?? 0) > 1">
          <button
            type="button"
            class="act-btn"
            :disabled="versionIndex === 0"
            title="上一版本"
            @click="emit('version', -1)"
          >‹</button>
          <span class="ver-indicator">{{ (versionIndex ?? 0) + 1 }}/{{ versionTotal }}</span>
          <button
            type="button"
            class="act-btn"
            :disabled="versionIndex === (versionTotal ?? 1) - 1"
            title="下一版本"
            @click="emit('version', 1)"
          >›</button>
        </template>
        <template v-if="actionable">
          <button
            type="button"
            class="act-btn"
            :class="{ active: message.feedback === 'LIKE' }"
            title="有用"
            @click="emit('feedback', 'LIKE')"
          >👍</button>
          <button
            type="button"
            class="act-btn"
            :class="{ active: message.feedback === 'DISLIKE' }"
            title="没帮助"
            @click="emit('feedback', 'DISLIKE')"
          >👎</button>
        </template>
        <button
          v-if="canRegenerate"
          type="button"
          class="act-btn regenerate"
          title="重新生成回复"
          @click="emit('regenerate')"
        >↻ 重新生成</button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.bubble-wrap {
  display: flex;
  flex-direction: column;
  max-width: 72%;
  min-width: 0;
}
.bubble-wrap.is-user {
  align-items: flex-end;
}
.bubble-wrap.is-assistant {
  align-items: flex-start;
}

.bubble {
  padding: 10px 14px;
  line-height: 1.6;
  word-break: break-word;
  border-radius: var(--radius-bubble);
  max-width: 100%;
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

/* ---------- 工具调用轨迹 ---------- */
.tool-trace {
  margin-top: 4px;
  width: 100%;
  list-style: none;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.tool-item {
  display: flex;
  flex-direction: column;
  gap: 1px;
  padding: 4px 8px;
  border-radius: 6px;
  background: color-mix(in srgb, var(--text-muted) 10%, transparent);
}

.tool-name {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-secondary);
}

.tool-result {
  font-size: 12px;
  color: var(--text-muted);
  word-break: break-all;
  white-space: pre-wrap;
}

/* ---------- RAG 引用溯源 ---------- */
.citations {
  margin-top: 4px;
  width: 100%;
}

.cite-toggle {
  padding: 2px 6px;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: var(--text-muted);
  font-size: 12px;
  cursor: pointer;
  transition: all var(--duration-base) var(--ease-base);
}
.cite-toggle:hover {
  background: var(--bg-hover);
  color: var(--text-primary);
}
.cite-caret {
  font-size: 10px;
}

.cite-list {
  margin: 4px 0 0;
  padding: 8px 10px;
  list-style: none;
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 8px;
  background: rgba(0, 0, 0, 0.2);
  max-width: 100%;
  box-sizing: border-box;
}
.cite-item + .cite-item {
  margin-top: 8px;
}
.cite-head {
  margin: 0 0 2px;
  display: flex;
  justify-content: space-between;
  gap: 8px;
}
.cite-name {
  font-size: 12px;
  color: var(--brand-to);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.cite-score {
  font-size: 11px;
  color: var(--text-muted);
  flex-shrink: 0;
}
.cite-snippet {
  margin: 0;
  font-size: 12px;
  line-height: 1.5;
  color: var(--text-secondary);
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

/* ---------- 操作行 ---------- */.msg-actions {
  display: flex;
  align-items: center;
  gap: 2px;
  margin-top: 4px;
  opacity: 0;
  transition: opacity var(--duration-base) var(--ease-base);
}
.bubble-wrap:hover .msg-actions,
.msg-actions:focus-within {
  opacity: 1;
}

.act-btn {
  padding: 2px 6px;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: var(--text-muted);
  font-size: 12px;
  line-height: 1.4;
  cursor: pointer;
  transition: all var(--duration-base) var(--ease-base);
}
.act-btn:hover:not(:disabled) {
  background: var(--bg-hover);
  color: var(--text-primary);
}
.act-btn:disabled {
  opacity: 0.35;
  cursor: not-allowed;
}
.act-btn.active {
  color: var(--brand-to);
}
.act-btn.regenerate {
  color: var(--text-secondary);
}
.act-btn.regenerate:hover {
  color: var(--brand-to);
}

.ver-indicator {
  min-width: 30px;
  text-align: center;
  font-size: 11px;
  color: var(--text-muted);
  font-variant-numeric: tabular-nums;
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
