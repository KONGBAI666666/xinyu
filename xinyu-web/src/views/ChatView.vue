<script setup lang="ts">
/**
 * 聊天主界面（契约三 3.4: <150 行, 纯编排）
 *
 * 布局: 左侧会话列表 | 右侧 Header / 消息区 / 输入框
 * SSE 流式聊天: 连接由 useSseChat 管理, 这里只调 send/stop
 *
 * M2.2: 「+ 新聊天」改为角色选择, ConversationList 通过 select 事件传 'new:<characterId>' 触发创建;
 *       也支持外部跳转 /chat?new=<characterId>（角色管理页「开聊」按钮）
 */
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useConversationStore } from '@/stores/conversation'
import { useMessageStore } from '@/stores/message'
import { useStatsStore } from '@/stores/stats'
import { useSseChat } from '@/composables/useSseChat'
import { BizError } from '@/utils/BizError'
import ConversationList from '@/components/chat/ConversationList.vue'
import ChatHeader from '@/components/chat/ChatHeader.vue'
import MessageList from '@/components/chat/MessageList.vue'
import ChatInput from '@/components/chat/ChatInput.vue'
import UsagePanel from '@/components/chat/UsagePanel.vue'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const conversationStore = useConversationStore()
const messageStore = useMessageStore()
const statsStore = useStatsStore()
const { send, stop } = useSseChat()

const creating = ref(false)
const errorText = ref('')

onMounted(() => {
  // 校准用户信息（token 失效由拦截器兜底跳登录）
  authStore.fetchMe()
  conversationStore.fetchList().catch(showError)
  // 外部跳转带 ?new=characterId 时自动创建会话（角色管理页「开聊」按钮）
  const newCharId = route.query.new
  if (typeof newCharId === 'string' && newCharId) {
    createConversation(newCharId)
    // 清掉 query 避免刷新重复创建
    router.replace({ path: '/chat' })
  }
})

// 离开页面: abort 进行中的生成（后端检测断连置 STOPPED）
onBeforeUnmount(stop)

// 切换当前会话 → abort 旧连接 + 清空旧消息并加载历史
watch(
  () => conversationStore.activeId,
  (id) => {
    stop()
    messageStore.clear()
    if (id) {
      messageStore.load(id).catch(showError)
    }
  },
)

async function createConversation(characterId: string, kbId: string | null = null): Promise<void> {
  if (creating.value) return
  creating.value = true
  try {
    await conversationStore.create(characterId, undefined, kbId)
  } catch (e) {
    showError(e)
  } finally {
    creating.value = false
  }
}

/**
 * 处理列表选择: 普通会话 id 直接切换; 'new:<characterId>' 前缀触发创建
 * 第二个参数 kbId 仅在新建会话时携带 (M3 RAG: 会话绑定知识库)
 */
function handleSelect(id: string, kbId?: string | null): void {
  if (id.startsWith('new:')) {
    createConversation(id.slice(4), kbId ?? null)
    return
  }
  conversationStore.setActive(id)
}

async function handleSend(content: string): Promise<void> {
  const conversationId = conversationStore.activeId
  if (!conversationId) return
  try {
    await send(conversationId, content)
  } catch (e) {
    showError(e)
  } finally {
    // 终态后刷新列表冗余字段（摘要/排序由后端维护, 标题不再自动改名）
    conversationStore.fetchList().catch(() => {})
    // 面板若打开则同步刷新, 让用户看到 token 数字实时跳动
    if (statsStore.panelOpen) {
      statsStore.load().catch(() => {})
    }
  }
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
      <ConversationList :creating="creating" @select="handleSelect" />
    </div>

    <div class="flex min-w-0 flex-1 flex-col">
      <ChatHeader :title="conversationStore.active?.title ?? ''" @logout="handleLogout" />
      <MessageList :has-active="!!conversationStore.activeId" />
      <ChatInput
        :disabled="!conversationStore.activeId"
        :streaming="messageStore.streaming"
        @send="handleSend"
        @stop="stop"
      />
    </div>

    <!-- 轻量错误提示（XyToast 组件延后, M1-4.3 不扩基础组件） -->
    <p v-if="errorText" class="toast text-sm">{{ errorText }}</p>

    <!-- 用量统计面板（右侧滑出, TRAE 风） -->
    <UsagePanel />
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
