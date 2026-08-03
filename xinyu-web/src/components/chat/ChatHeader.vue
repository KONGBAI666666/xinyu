<script setup lang="ts">
/**
 * 聊天区顶栏: 模型切换器 + 当前会话标题 + 统计入口 + 用户昵称 + 退出登录
 */
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useStatsStore } from '@/stores/stats'
import { useModelsStore } from '@/stores/models'
import { useConversationStore } from '@/stores/conversation'
import { modelsApi } from '@/api/modules/models'
import type { AiModelVO } from '@/types/api'

const router = useRouter()
const authStore = useAuthStore()
const statsStore = useStatsStore()
const modelsStore = useModelsStore()
const conversationStore = useConversationStore()

defineProps<{
  title: string
}>()

defineEmits<{
  logout: []
}>()

/** 模型选择下拉是否展开 */
const modelDropdownOpen = ref(false)

/** 当前会话生效的模型: 会话级覆盖 > 用户默认 */
const activeModel = computed<AiModelVO | null>(() => {
  const conv = conversationStore.active
  if (conv?.modelId) {
    return modelsStore.getById(conv.modelId)
  }
  return modelsStore.defaultModel()
})

/** 会话激活时预加载模型列表（用于切换器显示） */
watch(
  () => conversationStore.activeId,
  async (id) => {
    if (id) {
      await modelsStore.load().catch(() => {})
    }
  },
  { immediate: true }
)

async function switchModel(model: AiModelVO | null): Promise<void> {
  const convId = conversationStore.activeId
  if (!convId) return
  const targetId = model?.id ?? null
  // 与当前一致则不调用
  const currentId = conversationStore.active?.modelId ?? null
  if (targetId === currentId) {
    modelDropdownOpen.value = false
    return
  }
  try {
    await modelsApi.switchConversationModel(convId, targetId)
    // 本地更新会话的 modelId, 避免重新拉列表
    if (conversationStore.active) {
      conversationStore.active.modelId = targetId
    }
  } catch (e) {
    // 失败静默, 下次进入会从后端同步
  }
  modelDropdownOpen.value = false
}

function toggleDropdown(): void {
  modelDropdownOpen.value = !modelDropdownOpen.value
}

function closeDropdown(): void {
  modelDropdownOpen.value = false
}
</script>

<template>
  <header class="header flex items-center justify-between px-6">
    <!-- 左侧: 标题 -->
    <div class="header-left">
      <!-- 模型切换器 -->
      <div class="model-switcher">
        <button type="button" class="model-btn" :disabled="!conversationStore.activeId" @click.stop="toggleDropdown">
          <span class="model-dot" :class="{ active: !!activeModel }"></span>
          <span class="model-name">{{ activeModel ? activeModel.displayName : '默认模型' }}</span>
          <svg
            v-if="conversationStore.activeId"
            class="chevron"
            :class="{ open: modelDropdownOpen }"
            width="12" height="12" viewBox="0 0 12 12" fill="none"
          >
            <path d="M3 4.5L6 7.5L9 4.5" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
        </button>

        <!-- 外部点击遮罩 -->
        <div v-if="modelDropdownOpen" class="outside-mask" @click="closeDropdown"></div>

        <!-- 下拉菜单 -->
        <Transition name="dropdown">
          <div v-if="modelDropdownOpen" class="dropdown" @click.stop>
            <!-- 默认模型选项（清除会话级覆盖） -->
            <div
              class="dropdown-item"
              :class="{ selected: !conversationStore.active?.modelId }"
              @click="switchModel(null)"
            >
              <div class="item-info">
                <span class="item-name">用户默认</span>
                <span class="item-desc">{{ modelsStore.defaultModel()?.displayName ?? '未设置' }}</span>
              </div>
              <svg v-if="!conversationStore.active?.modelId" class="check" width="14" height="14" viewBox="0 0 14 14" fill="none">
                <path d="M2 7L6 11L12 3" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/>
              </svg>
            </div>

            <div class="dropdown-divider"></div>

            <!-- 模型列表 -->
            <div
              v-for="model in modelsStore.list"
              :key="model.id"
              class="dropdown-item"
              :class="{ selected: conversationStore.active?.modelId === model.id }"
              @click="switchModel(model)"
            >
              <div class="item-info">
                <span class="item-name">{{ model.displayName }}</span>
                <span class="item-desc">{{ model.provider }} · {{ model.modelCode }}</span>
              </div>
              <svg v-if="conversationStore.active?.modelId === model.id" class="check" width="14" height="14" viewBox="0 0 14 14" fill="none">
                <path d="M2 7L6 11L12 3" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/>
              </svg>
            </div>

            <!-- 空状态 / 管理 -->
            <div v-if="modelsStore.list.length === 0" class="dropdown-empty">
              还没有添加模型
            </div>

            <div class="dropdown-divider"></div>
            <div class="dropdown-item manage" @click="router.push('/settings/models'); closeDropdown()">
              <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
                <path d="M7 2V12M2 7H12" stroke="currentColor" stroke-width="1.4" stroke-linecap="round"/>
              </svg>
              <span>管理模型</span>
            </div>
            <div class="dropdown-item manage" @click="router.push('/settings/memories'); closeDropdown()">
              <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
                <path d="M7 2C4 2 2 4 2 6.5C2 8 2.8 9 3.8 9.8L3.5 12L5.6 11C6 11.1 6.5 11.2 7 11.2C10 11.2 12 9.2 12 6.6C12 4 10 2 7 2Z" stroke="currentColor" stroke-width="1.2" stroke-linejoin="round"/>
                <circle cx="5" cy="6.5" r="0.6" fill="currentColor"/>
                <circle cx="7" cy="6.5" r="0.6" fill="currentColor"/>
                <circle cx="9" cy="6.5" r="0.6" fill="currentColor"/>
              </svg>
              <span>长期记忆</span>
            </div>
            <div class="dropdown-item manage" @click="router.push('/settings/knowledge-bases'); closeDropdown()">
              <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
                <path d="M2 3.5C2 2.7 2.7 2 3.5 2H6V12H3.5C2.7 12 2 11.3 2 10.5V3.5Z" stroke="currentColor" stroke-width="1.2"/>
                <path d="M8 2H10.5C11.3 2 12 2.7 12 3.5V10.5C12 11.3 11.3 12 10.5 12H8V2Z" stroke="currentColor" stroke-width="1.2"/>
              </svg>
              <span>知识库 (RAG)</span>
            </div>
          </div>
        </Transition>
      </div>

      <span class="title-sep">/</span>
      <span class="title text-sm font-medium">{{ title || '选择或创建一个会话' }}</span>
    </div>

    <!-- 右侧: 统计 + 用户 -->
    <div class="flex items-center gap-3">
      <button
        type="button"
        class="stats-btn"
        title="用量统计"
        @click="statsStore.openPanel()"
      >
        <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
          <path
            d="M2 13V3M2 13H14M5 13V8M8 13V5M11 13V10M14 13V6"
            stroke="currentColor"
            stroke-width="1.5"
            stroke-linecap="round"
            stroke-linejoin="round"
          />
        </svg>
      </button>
      <span class="nickname text-sm">{{ authStore.user?.nickname }}</span>
      <button type="button" class="logout-btn text-xs" @click="$emit('logout')">退出登录</button>
    </div>
  </header>
</template>

<style scoped>
.header {
  height: 56px;
  flex-shrink: 0;
  background: var(--bg-elevated);
  backdrop-filter: blur(var(--blur-glass));
}

.header-left {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
  flex: 1;
}

/* ---------- 模型切换器 ---------- */
.model-switcher {
  position: relative;
}

.model-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 5px 10px;
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.03);
  color: var(--text-secondary);
  font-size: 12px;
  cursor: pointer;
  transition: all var(--duration-base) var(--ease-base);
  white-space: nowrap;
}
.model-btn:hover:not(:disabled) {
  background: var(--bg-hover);
  border-color: rgba(255, 255, 255, 0.12);
  color: var(--text-primary);
}
.model-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.model-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--text-muted);
}
.model-dot.active {
  background: var(--brand-to);
  box-shadow: 0 0 6px rgba(94, 234, 212, 0.5);
}

.model-name {
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
}

.chevron {
  transition: transform var(--duration-base) var(--ease-base);
  color: var(--text-muted);
}
.chevron.open {
  transform: rotate(180deg);
}

/* ---------- 下拉 ---------- */
.outside-mask {
  position: fixed;
  inset: 0;
  z-index: 19;
}

.dropdown {
  position: absolute;
  top: calc(100% + 6px);
  left: 0;
  z-index: 20;
  width: 260px;
  padding: 6px;
  border-radius: 10px;
  background: rgba(20, 24, 40, 0.95);
  backdrop-filter: blur(var(--blur-glass));
  border: 1px solid rgba(255, 255, 255, 0.08);
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.4);
}

.dropdown-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 10px;
  border-radius: 6px;
  cursor: pointer;
  transition: background var(--duration-base) var(--ease-base);
}
.dropdown-item:hover {
  background: var(--bg-hover);
}
.dropdown-item.selected {
  background: rgba(94, 234, 212, 0.08);
}

.item-info {
  display: flex;
  flex-direction: column;
  gap: 1px;
  min-width: 0;
}
.item-name {
  font-size: 13px;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
}
.item-desc {
  font-size: 11px;
  color: var(--text-muted);
  overflow: hidden;
  text-overflow: ellipsis;
}

.check {
  color: var(--brand-to);
  flex-shrink: 0;
}

.dropdown-divider {
  height: 1px;
  margin: 4px 6px;
  background: rgba(255, 255, 255, 0.06);
}

.dropdown-empty {
  padding: 16px;
  text-align: center;
  font-size: 12px;
  color: var(--text-muted);
}

.dropdown-item.manage {
  gap: 8px;
  color: var(--text-secondary);
  font-size: 12px;
}
.dropdown-item.manage:hover {
  color: var(--brand-to);
}

/* ---------- 其他 ---------- */
.title-sep {
  color: var(--text-muted);
  opacity: 0.4;
}

.title {
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.nickname {
  color: var(--text-secondary);
}

.stats-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border: none;
  border-radius: var(--radius-btn);
  background: transparent;
  color: var(--text-muted);
  cursor: pointer;
  transition: all var(--duration-base) var(--ease-base);
}

.stats-btn:hover {
  background: var(--bg-hover);
  color: var(--brand-to);
}

.logout-btn {
  padding: 5px 12px;
  border: none;
  border-radius: var(--radius-btn);
  background: transparent;
  color: var(--text-muted);
  cursor: pointer;
  transition: all var(--duration-base) var(--ease-base);
}

.logout-btn:hover {
  background: var(--bg-hover);
  color: var(--text-secondary);
}

/* ---------- 动画 ---------- */
.dropdown-enter-active, .dropdown-leave-active {
  transition: all var(--duration-base) var(--ease-base);
}
.dropdown-enter-from, .dropdown-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}
</style>
