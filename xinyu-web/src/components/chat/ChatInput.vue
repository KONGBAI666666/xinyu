<script setup lang="ts">
/**
 * 输入框: Enter 发送 / Shift+Enter 换行 / 空内容禁止
 * 生成中: 输入框禁用, 发送按钮变「停止」（契约二 2.3 单会话串行）
 * 只负责收集输入并 emit, 发送/停止编排在 ChatView
 */
import { computed, ref } from 'vue'

const props = defineProps<{
  /** 未选中会话时禁用 */
  disabled: boolean
  /** 生成中: 锁定输入, 按钮切「停止」 */
  streaming: boolean
}>()

const emit = defineEmits<{
  send: [content: string]
  stop: []
}>()

const draft = ref('')
const canSend = computed(
  () => !props.disabled && !props.streaming && draft.value.trim().length > 0,
)

function handleSend(): void {
  if (!canSend.value) return
  emit('send', draft.value.trim())
  draft.value = ''
}

function handleKeydown(event: KeyboardEvent): void {
  // Shift+Enter 走默认换行; 输入法合成中的 Enter 不发送
  if (event.key === 'Enter' && !event.shiftKey && !event.isComposing) {
    event.preventDefault()
    handleSend()
  }
}
</script>

<template>
  <div class="input-bar px-6 py-4">
    <div class="input-box mx-auto flex max-w-3xl items-end gap-3">
      <textarea
        v-model="draft"
        class="field flex-1 resize-none text-sm"
        rows="2"
        maxlength="2000"
        :disabled="disabled || streaming"
        :placeholder="
          disabled
            ? '先选择或创建一个会话'
            : streaming
              ? '屿屿正在回复…'
              : '说点什么吧…（Enter 发送，Shift+Enter 换行）'
        "
        @keydown="handleKeydown"
      ></textarea>
      <!-- 生成中切「停止」: 单会话串行由前端保证 -->
      <button
        v-if="streaming"
        type="button"
        class="stop-btn shrink-0 text-sm"
        @click="$emit('stop')"
      >
        停止
      </button>
      <button
        v-else
        type="button"
        class="send-btn shrink-0 text-sm"
        :disabled="!canSend"
        @click="handleSend"
      >
        发送
      </button>
    </div>
  </div>
</template>

<style scoped>
.input-bar {
  flex-shrink: 0;
}

.field {
  padding: 10px 14px;
  border: 1px solid transparent;
  border-radius: var(--radius-btn);
  background: var(--bg-elevated);
  color: var(--text-primary);
  outline: none;
  transition: border-color var(--duration-base) var(--ease-base);
}

.field::placeholder {
  color: var(--text-muted);
}

.field:focus {
  border-color: var(--brand-from);
}

.field:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.send-btn {
  padding: 10px 20px;
  border: none;
  border-radius: var(--radius-btn);
  background: var(--brand-gradient);
  color: #fff;
  cursor: pointer;
  transition: opacity var(--duration-base) var(--ease-base);
}

.send-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.stop-btn {
  padding: 10px 20px;
  border: 1px solid var(--color-danger);
  border-radius: var(--radius-btn);
  background: transparent;
  color: var(--color-danger);
  cursor: pointer;
  transition: background var(--duration-base) var(--ease-base);
}

.stop-btn:hover {
  background: var(--bg-hover);
}
</style>
