<script setup lang="ts">
/**
 * 消息区域: 历史消息 + 流式气泡, 新消息/流式追加时自动滚到底部;
 * 滚到顶部时向上加载更早历史（游标分页）并保持滚动位置不跳动
 */
import { nextTick, ref, watch } from 'vue'
import { PAGE_SIZE, useMessageStore } from '@/stores/message'
import { useConversationStore } from '@/stores/conversation'
import MessageBubble from './MessageBubble.vue'

const messageStore = useMessageStore()
const conversationStore = useConversationStore()
const scrollRef = ref<HTMLElement | null>(null)
/** 前置插入旧消息期间, 抑制"滚到底部"的自动滚动 */
const suppressAutoScroll = ref(false)

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
    if (suppressAutoScroll.value) return
    await nextTick()
    scrollRef.value?.scrollTo({ top: scrollRef.value.scrollHeight })
  },
)

/** 滚到顶部附近时加载更早的一页, 加载后保持视口位置稳定 */
async function handleScroll(): Promise<void> {
  const el = scrollRef.value
  if (!el || el.scrollTop > 80) return
  const conversationId = conversationStore.activeId
  if (!conversationId || messageStore.loadingMore || !messageStore.hasMore) return
  suppressAutoScroll.value = true
  const prevHeight = el.scrollHeight
  const prevTop = el.scrollTop
  try {
    await messageStore.loadMore(conversationId)
    await nextTick()
    el.scrollTop = el.scrollHeight - prevHeight + prevTop
  } finally {
    suppressAutoScroll.value = false
  }
}
</script>

<template>
  <div ref="scrollRef" class="flex-1 overflow-y-auto px-6 py-4" @scroll.passive="handleScroll">
    <p v-if="!hasActive" class="hint text-center text-sm">
      选择左侧会话，或点「+ 新聊天」开始
    </p>
    <p v-else-if="messageStore.loadingHistory" class="hint text-center text-sm">加载消息中…</p>
    <template v-else>
      <div class="mx-auto flex max-w-3xl flex-col gap-3">
        <p v-if="messageStore.loadingMore" class="more-hint text-center text-xs">加载更早消息…</p>
        <p
          v-else-if="!messageStore.hasMore && messageStore.items.length >= PAGE_SIZE"
          class="more-hint text-center text-xs"
        >
          已显示全部消息
        </p>
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
.more-hint {
  padding: 4px 0;
  color: var(--text-muted);
}
</style>
