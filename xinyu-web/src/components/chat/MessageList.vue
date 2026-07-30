<script setup lang="ts">
/**
 * 消息区域: 历史消息 + 流式气泡, 新消息/流式追加时自动滚到底部
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

// 消息数变化（加载/发送）或流式文本增长时滚到底部
watch(
  () => {
    const last = messageStore.items[messageStore.items.length - 1]
    return [messageStore.items.length, last?.content.length ?? 0]
  },
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
      </div>
    </template>
  </div>
</template>

<style scoped>
.hint {
  margin-top: 48px;
  color: var(--text-muted);
}
</style>
