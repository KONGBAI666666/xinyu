<script setup lang="ts">
/**
 * 聊天主界面（契约三 3.4: <150 行, 纯编排）
 *
 * 布局: 左侧会话列表 | 右侧 Header / 消息区 / 输入框
 * M1-4.3 真实 API 数据, 发送仅本地乐观插入; SSE 流式随 M1-4.4 接入
 */
import { onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useConversationStore } from '@/stores/conversation'
import { useMessageStore } from '@/stores/message'
import { BizError } from '@/utils/BizError'
import ConversationList from '@/components/chat/ConversationList.vue'
import ChatHeader from '@/components/chat/ChatHeader.vue'
import MessageList from '@/components/chat/MessageList.vue'
import ChatInput from '@/components/chat/ChatInput.vue'

/** 官方角色「屿屿」（seed-dev.sql 固定 id, 决策 D1）; M2 角色广场后改为用户选择 */
const OFFICIAL_CHARACTER_ID = '1001'

const router = useRouter()
const authStore = useAuthStore()
const conversationStore = useConversationStore()
const messageStore = useMessageStore()

const creating = ref(false)
const errorText = ref('')

onMounted(() => {
  // 校准用户信息（token 失效由拦截器兜底跳登录）
  authStore.fetchMe()
  conversationStore.fetchList().catch(showError)
})

// 切换当前会话 → 清空旧消息并加载历史
watch(
  () => conversationStore.activeId,
  (id) => {
    messageStore.clear()
    if (id) {
      messageStore.load(id).catch(showError)
    }
  },
)

async function handleCreate(): Promise<void> {
  if (creating.value) return
  creating.value = true
  try {
    await conversationStore.create(OFFICIAL_CHARACTER_ID)
  } catch (e) {
    showError(e)
  } finally {
    creating.value = false
  }
}

function handleSelect(id: string): void {
  conversationStore.setActive(id)
}

function handleSend(content: string): void {
  if (!conversationStore.activeId) return
  // M1-4.3: 仅本地插入用户消息 + 「等待AI回复」占位, 不调聊天接口
  messageStore.appendUserMessage(conversationStore.activeId, content)
}

function handleLogout(): void {
  authStore.logout()
  router.push('/login')
}

function showError(e: unknown): void {
  errorText.value = e instanceof BizError ? e.message : '出了点小状况，请稍后重试'
  window.setTimeout(() => {
    errorText.value = ''
  }, 3000)
}
</script>

<template>
  <div class="flex h-full">
    <div class="w-64 shrink-0">
      <ConversationList :creating="creating" @create="handleCreate" @select="handleSelect" />
    </div>

    <div class="flex min-w-0 flex-1 flex-col">
      <ChatHeader :title="conversationStore.active?.title ?? ''" @logout="handleLogout" />
      <MessageList :has-active="!!conversationStore.activeId" />
      <ChatInput :disabled="!conversationStore.activeId" @send="handleSend" />
    </div>

    <!-- 轻量错误提示（XyToast 组件延后, M1-4.3 不扩基础组件） -->
    <p v-if="errorText" class="toast text-sm">{{ errorText }}</p>
  </div>
</template>

<style scoped>
.toast {
  position: fixed;
  top: 20px;
  left: 50%;
  transform: translateX(-50%);
  padding: 8px 20px;
  border-radius: var(--radius-btn);
  background: var(--bg-elevated);
  backdrop-filter: blur(var(--blur-glass));
  color: var(--color-danger);
}
</style>
