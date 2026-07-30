<script setup lang="ts">
/**
 * 聊天区顶栏: 当前会话标题 + 用户昵称 + 退出登录
 */
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()

defineProps<{
  title: string
}>()

defineEmits<{
  logout: []
}>()
</script>

<template>
  <header class="header flex items-center justify-between px-6">
    <span class="title text-sm font-medium">{{ title || '选择或创建一个会话' }}</span>
    <div class="flex items-center gap-3">
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

.title {
  color: var(--text-primary);
}

.nickname {
  color: var(--text-secondary);
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
</style>
