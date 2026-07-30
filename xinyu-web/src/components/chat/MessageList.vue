<script setup lang="ts">
/**
 * 消息区域: 历史消息 + 「等待AI回复」占位, 新消息自动滚到底部
 */
import { nextTick, ref, watch } from 'vue'
import { useMessageStore } from '@/stores/message'
import MessageBubble from './MessageBubble.vue'

const messageStore = useMessageStore()
const scrollRef = ref<HTMLElement | null>(null)

defineProps<{
  /** 是否已选中会话（未选中显示引导空态） */
  hasActive: boolean
}>()

// 消息变化（加载历史/发送）后滚到底部
watch(
  () => [messageStore.items.length, messageStore.awaitingReply],
  async () => {
    await nextTick()
    scrollRef.value?.scrollTo({ top: scrollRef.value.scrollHeight })
  },
)
</script>

<template>
  <div ref="scrollRef" class="flex-1 overflow-y-auto px-6 py-4">
    <p v-if="!hasActive" class="hint text-center text-sm">
      选择左侧会话，或点「+ 新聊天」开始
    </p>
    <p v-else-if="messageStore.loadingHistory" class="hint text-center text-sm">加载消息中…</p>
    <template v-else>
      <div class="mx-auto flex max-w-3xl flex-col gap-3">
        <MessageBubble v-for="item in messageStore.items" :key="item.id" :message="item" />
        <!-- M1-4.3 发送后占位; M1-4.4 替换为 SSE 流式气泡 -->
        <p v-if="messageStore.awaitingReply" class="waiting text-xs">等待AI回复...</p>
      </div>
    </template>
  </div>
</template>

<style scoped>
.hint {
  margin-top: 48px;
  color: var(--text-muted);
}

.waiting {
  color: var(--text-muted);
}
</style>
