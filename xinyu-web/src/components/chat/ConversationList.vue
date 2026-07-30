<script setup lang="ts">
/**
 * 左侧会话列表栏: 品牌区 + 「+ 新聊天」 + 会话列表
 * 数据来自 conversation store; 创建/切换的后续编排（加载消息）由 ChatView 负责
 */
import { useConversationStore } from '@/stores/conversation'
import ConversationItem from './ConversationItem.vue'

const conversationStore = useConversationStore()

defineProps<{
  /** 创建中: 防止「+ 新聊天」重复点击 */
  creating: boolean
}>()

defineEmits<{
  create: []
  select: [id: string]
}>()
</script>

<template>
  <aside class="sidebar flex h-full flex-col">
    <h1 class="brand px-4 pt-5 text-xl font-bold">心屿</h1>

    <div class="px-3 pt-4">
      <button type="button" class="new-btn w-full py-2 text-sm" :disabled="creating" @click="$emit('create')">
        {{ creating ? '创建中…' : '+ 新聊天' }}
      </button>
    </div>

    <nav class="mt-3 flex-1 overflow-y-auto px-3 pb-4">
      <p v-if="conversationStore.loading" class="hint text-center text-xs">加载中…</p>
      <p v-else-if="conversationStore.list.length === 0" class="hint text-center text-xs">
        还没有会话，点上方开始聊天
      </p>
      <div v-else class="flex flex-col gap-1">
        <ConversationItem
          v-for="item in conversationStore.list"
          :key="item.id"
          :conversation="item"
          :active="item.id === conversationStore.activeId"
          @select="$emit('select', $event)"
        />
      </div>
    </nav>
  </aside>
</template>

<style scoped>
.sidebar {
  background: var(--bg-elevated);
  backdrop-filter: blur(var(--blur-glass));
}

.brand {
  background: var(--brand-gradient);
  background-clip: text;
  -webkit-background-clip: text;
  color: transparent;
}

.new-btn {
  border: none;
  border-radius: var(--radius-btn);
  background: var(--brand-gradient);
  color: #fff;
  cursor: pointer;
  transition: opacity var(--duration-base) var(--ease-base);
}

.new-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.hint {
  margin-top: 24px;
  color: var(--text-muted);
}
</style>
