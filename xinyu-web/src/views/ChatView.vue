<script setup lang="ts">
/**
 * 聊天页占位（M1-4.2 仅用于验证路由守卫与登录态恢复）
 * M1-4.3 替换为三栏聊天界面: ConversationList / MessageList / ChatInput
 */
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const authStore = useAuthStore()

// 刷新进入时校准用户信息, 同时验证 token 有效性（失效由拦截器兜底跳登录）
onMounted(() => {
  authStore.fetchMe()
})

function handleLogout(): void {
  authStore.logout()
  router.push('/login')
}
</script>

<template>
  <div class="flex h-full flex-col items-center justify-center gap-4">
    <p class="text-lg">
      你好，<span :style="{ color: 'var(--brand-from)' }">{{ authStore.user?.nickname }}</span>
    </p>
    <p class="text-sm" :style="{ color: 'var(--text-muted)' }">
      M1-4.2 · 聊天界面将在 M1-4.3 到达这里
    </p>
    <button class="logout-btn text-sm" type="button" @click="handleLogout">退出登录</button>
  </div>
</template>

<style scoped>
.logout-btn {
  padding: 6px 16px;
  border: none;
  border-radius: var(--radius-btn);
  background: var(--bg-elevated);
  color: var(--text-secondary);
  cursor: pointer;
  transition: background var(--duration-base) var(--ease-base);
}

.logout-btn:hover {
  background: var(--bg-hover);
}
</style>
