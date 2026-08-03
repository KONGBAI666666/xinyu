<script setup lang="ts">
/**
 * 左侧会话列表栏: 品牌区 + 「+ 新聊天」(角色选择) + 会话列表 + 角色管理入口
 * 数据来自 conversation store; 创建/切换的后续编排（加载消息）由 ChatView 负责
 */
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useConversationStore } from '@/stores/conversation'
import { useCharactersStore } from '@/stores/character'
import ConversationItem from './ConversationItem.vue'

const router = useRouter()
const conversationStore = useConversationStore()
const charactersStore = useCharactersStore()

defineProps<{
  /** 创建中: 防止重复点击 */
  creating: boolean
}>()

defineEmits<{
  select: [id: string]
}>()

/** 角色选择浮层 */
const pickerOpen = ref(false)

onMounted(() => {
  // 预加载角色列表用于「+ 新聊天」选择
  charactersStore.load().catch(() => {})
})

function togglePicker(): void {
  pickerOpen.value = !pickerOpen.value
}

function closePicker(): void {
  pickerOpen.value = false
}

defineExpose({ closePicker })
</script>

<template>
  <aside class="sidebar flex h-full flex-col">
    <h1 class="brand px-4 pt-5 text-xl font-bold">心屿</h1>

    <div class="px-3 pt-4 relative">
      <button
        type="button"
        class="new-btn w-full py-2 text-sm"
        :disabled="creating"
        @click="togglePicker"
      >
        {{ creating ? '创建中…' : '+ 新聊天' }}
      </button>

      <!-- 外部点击遮罩 -->
      <div v-if="pickerOpen" class="picker-mask" @click="closePicker"></div>

      <!-- 角色选择浮层 -->
      <Transition name="picker">
        <div v-if="pickerOpen" class="picker" @click.stop>
          <p class="picker-title">选择角色开聊</p>
          <button
            v-for="c in charactersStore.list"
            :key="c.id"
            type="button"
            class="picker-item"
            @click="$emit('select', `new:${c.id}`); closePicker()"
          >
            <div class="pi-avatar" :style="c.avatarUrl ? `background-image:url(${c.avatarUrl})` : ''">
              <span v-if="!c.avatarUrl">{{ c.name.charAt(0) }}</span>
            </div>
            <div class="pi-info">
              <span class="pi-name">{{ c.name }}</span>
              <span class="pi-intro">{{ c.intro || (c.mine ? '我创建的角色' : '官方角色') }}</span>
            </div>
            <span v-if="c.creatorType === 'OFFICIAL'" class="pi-tag">官方</span>
          </button>

          <div class="picker-divider"></div>
          <button class="picker-manage" type="button" @click="router.push('/settings/characters'); closePicker()">
            <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
              <path d="M7 2V12M2 7H12" stroke="currentColor" stroke-width="1.4" stroke-linecap="round"/>
            </svg>
            <span>管理角色</span>
          </button>
        </div>
      </Transition>
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

/* ---------- 角色选择浮层 ---------- */
.picker-mask {
  position: fixed;
  inset: 0;
  z-index: 19;
}

.picker {
  position: absolute;
  top: calc(100% + 6px);
  left: 12px;
  right: 12px;
  z-index: 20;
  padding: 6px;
  border-radius: 12px;
  background: rgba(20, 24, 40, 0.95);
  backdrop-filter: blur(var(--blur-glass));
  border: 1px solid rgba(255, 255, 255, 0.08);
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.4);
  max-height: 60vh;
  overflow-y: auto;
}

.picker-title {
  margin: 4px 8px 6px;
  font-size: 11px;
  color: var(--text-muted);
  letter-spacing: 0.04em;
}

.picker-item {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 8px 10px;
  border: none;
  border-radius: 8px;
  background: transparent;
  cursor: pointer;
  transition: background var(--duration-base) var(--ease-base);
  text-align: left;
}
.picker-item:hover { background: var(--bg-hover); }

.pi-avatar {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  background: var(--brand-gradient);
  background-size: cover;
  background-position: center;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 14px;
  font-weight: 600;
  flex-shrink: 0;
}

.pi-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 1px;
}
.pi-name {
  font-size: 13px;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.pi-intro {
  font-size: 11px;
  color: var(--text-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pi-tag {
  padding: 1px 6px;
  border-radius: 4px;
  background: rgba(94, 234, 212, 0.15);
  color: var(--brand-to);
  font-size: 10px;
  font-weight: 600;
  flex-shrink: 0;
}

.picker-divider {
  height: 1px;
  margin: 4px 6px;
  background: rgba(255, 255, 255, 0.06);
}

.picker-manage {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 8px 10px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: var(--text-secondary);
  font-size: 12px;
  cursor: pointer;
  transition: all var(--duration-base) var(--ease-base);
}
.picker-manage:hover { background: var(--bg-hover); color: var(--brand-to); }

/* ---------- 动画 ---------- */
.picker-enter-active, .picker-leave-active {
  transition: all var(--duration-base) var(--ease-base);
}
.picker-enter-from, .picker-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}
</style>
