<script setup lang="ts">
/**
 * 消息区域: 历史消息 + 流式气泡, 新消息/流式追加时自动滚到底部;
 * 滚到顶部时向上加载更早历史（游标分页）并保持滚动位置不跳动
 * M4+: 同一 parent 的连续 ASSISTANT 折叠为重新生成版本组 (‹ n/m › 切换, 默认展示最新)
 */
import { computed, nextTick, ref, watch } from 'vue'
import { PAGE_SIZE, useMessageStore } from '@/stores/message'
import { useConversationStore } from '@/stores/conversation'
import type { MessageFeedback, MessageVO } from '@/types/api'
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

const emit = defineEmits<{
  regenerate: []
  feedback: [messageId: string, value: MessageFeedback]
}>()

interface RenderItem {
  key: string
  message: MessageVO
  versionIndex: number
  versionTotal: number
  canRegenerate: boolean
}

/** 手动切换过的版本选择 (parent → 版本下标); 未手动切换时始终跟随最新版本 */
const versionPick = ref<Record<string, number>>({})

/** 同一 parent 的连续 ASSISTANT 为一个版本组; 其余逐条渲染 */
const rendered = computed<RenderItem[]>(() => {
  const items = messageStore.items
  const out: RenderItem[] = []
  let i = 0
  while (i < items.length) {
    const m = items[i]
    let entry: RenderItem
    if (m.messageType === 'ASSISTANT' && m.parentMessageId) {
      const parent = m.parentMessageId
      const versions: MessageVO[] = []
      while (
        i < items.length &&
        items[i].messageType === 'ASSISTANT' &&
        items[i].parentMessageId === parent
      ) {
        versions.push(items[i])
        i++
      }
      const picked = versionPick.value[parent]
      const index = Math.min(Math.max(picked ?? versions.length - 1, 0), versions.length - 1)
      entry = {
        key: `v-${parent}`,
        message: versions[index],
        versionIndex: index,
        versionTotal: versions.length,
        canRegenerate: false,
      }
    } else {
      entry = { key: m.id, message: m, versionIndex: 0, versionTotal: 1, canRegenerate: false }
      i++
    }
    out.push(entry)
  }
  // 「重新生成」只出现在最后一条 ASSISTANT 渲染项上, 且不在生成中
  if (!messageStore.streaming && out.length > 0) {
    const last = out[out.length - 1]
    if (last.message.messageType === 'ASSISTANT' && last.message.status !== 'GENERATING') {
      last.canRegenerate = true
    }
  }
  return out
})

function switchVersion(parent: string, dir: number, total: number): void {
  const current = versionPick.value[parent] ?? total - 1
  versionPick.value[parent] = Math.min(Math.max(current + dir, 0), total - 1)
}

function onFeedback(item: RenderItem, value: 'LIKE' | 'DISLIKE'): void {
  const next: MessageFeedback = item.message.feedback === value ? 'NONE' : value
  emit('feedback', item.message.id, next)
}

// 消息数变化（加载/发送）时滚到底部; 流式文本增长仅在用户本就在底部时跟随,
// 上滑回看历史时不被拽回 (想回底部手动下滑即可)
watch(
  () => {
    const last = messageStore.items[messageStore.items.length - 1]
    return { count: messageStore.items.length, len: last?.content.length ?? 0 }
  },
  async (next, prev) => {
    if (suppressAutoScroll.value) return
    // 仅流式追加 (条数不变) 且用户已离开底部: 不自动滚动
    const el = scrollRef.value
    const nearBottom = !el || el.scrollHeight - el.scrollTop - el.clientHeight < 80
    if (next.count === prev?.count && !nearBottom) return
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
  } catch {
    // 加载失败静默保留当前视图, 再滚到顶部可重试
  } finally {
    suppressAutoScroll.value = false
  }
}
</script>

<template>
  <div
    ref="scrollRef"
    class="flex-1 overflow-y-auto px-6 py-4 max-md:px-3"
    @scroll.passive="handleScroll"
  >
    <p v-if="!hasActive" class="hint text-center text-sm">选择左侧会话，或点「+ 新聊天」开始</p>
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
        <MessageBubble
          v-for="item in rendered"
          :key="item.key"
          :message="item.message"
          :version-index="item.versionTotal > 1 ? item.versionIndex : undefined"
          :version-total="item.versionTotal"
          :can-regenerate="item.canRegenerate"
          @version="
            (dir) =>
              item.message.parentMessageId &&
              switchVersion(item.message.parentMessageId, dir, item.versionTotal)
          "
          @feedback="(value) => onFeedback(item, value)"
          @regenerate="emit('regenerate')"
        />
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
