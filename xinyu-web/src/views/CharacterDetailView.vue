<script setup lang="ts">
/**
 * 角色详情页（M2.3）
 *
 * 布局:
 * - 顶部: 模糊头像背景 + 返回
 * - 中部: 大头像 + 名称 + intro + 官方/收藏数标签
 * - 开场白气泡预览
 * - 悬浮双按钮: 开始聊天 / 收藏
 *
 * 游客可访问, 点收藏/开聊引导登录。
 */
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { charactersApi } from '@/api/modules/character'
import { useAuthStore } from '@/stores/auth'
import { BizError } from '@/utils/BizError'
import type { CharacterVO } from '@/types/api'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const character = ref<CharacterVO | null>(null)
const loading = ref(true)
const errorText = ref('')

onMounted(async () => {
  const id = route.params.id as string
  if (!id) {
    router.replace('/square')
    return
  }
  try {
    character.value = await charactersApi.squareDetail(id)
    if (!character.value) {
      router.replace('/square')
      return
    }
  } catch (e) {
    errorText.value = e instanceof BizError ? e.message : '加载失败'
  } finally {
    loading.value = false
  }
})

async function toggleFavorite(): Promise<void> {
  if (!character.value) return
  if (!authStore.isLoggedIn) {
    redirectToLogin()
    return
  }
  try {
    if (character.value.favorited) {
      await charactersApi.unfavorite(character.value.id)
      character.value.favorited = false
      character.value.favoriteCount = Math.max(0, character.value.favoriteCount - 1)
    } else {
      await charactersApi.favorite(character.value.id)
      character.value.favorited = true
      character.value.favoriteCount += 1
    }
  } catch (e) {
    errorText.value = e instanceof BizError ? e.message : '操作失败'
  }
}

function startChat(): void {
  if (!character.value) return
  if (!authStore.isLoggedIn) {
    redirectToLogin()
    return
  }
  router.push({ path: '/chat', query: { new: character.value.id } })
}

function redirectToLogin(): void {
  const redirect = `/square/${route.params.id}`
  router.push({ path: '/login', query: { redirect } })
}
</script>

<template>
  <div class="page">
    <!-- 加载态 -->
    <div v-if="loading" class="state-hint">加载中…</div>

    <template v-else-if="character">
      <!-- 模糊背景 -->
      <div
        class="bg-blur"
        :style="character.avatarUrl ? `background-image:url(${character.avatarUrl})` : ''"
      ></div>
      <div class="bg-mask"></div>

      <!-- 返回 -->
      <button class="back-btn" type="button" @click="router.push('/square')">
        <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
          <path d="M10 12L6 8L10 4" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
        <span>广场</span>
      </button>

      <!-- 主内容 -->
      <div class="main">
        <div class="hero">
          <div class="avatar" :style="character.avatarUrl ? `background-image:url(${character.avatarUrl})` : ''">
            <span v-if="!character.avatarUrl">{{ character.name.charAt(0) }}</span>
          </div>

          <h1 class="name">
            {{ character.name }}
            <span v-if="character.creatorType === 'OFFICIAL'" class="official-tag">官方</span>
          </h1>

          <p class="intro">{{ character.intro || '暂无介绍' }}</p>

          <div class="stats">
            <span class="stat-item">
              <svg width="12" height="12" viewBox="0 0 12 12" fill="none">
                <path d="M2 4H10V9C10 9.5 9.5 10 9 10H3C2.5 10 2 9.5 2 9V4Z" stroke="currentColor" stroke-width="1"/>
                <path d="M2 4.5L6 7L10 4.5" stroke="currentColor" stroke-width="1" stroke-linejoin="round"/>
              </svg>
              {{ character.chatCount }} 对话
            </span>
            <span class="stat-item">
              <svg width="12" height="12" viewBox="0 0 12 12" fill="none">
                <path d="M6 10.5L1.8 6.8C0.5 5.6 0.5 3.7 1.8 2.6C3 1.6 4.7 1.9 5.5 3L6 3.7L6.5 3C7.3 1.9 9 1.6 10.2 2.6C11.5 3.7 11.5 5.6 10.2 6.8L6 10.5Z" stroke="currentColor" stroke-width="1" stroke-linejoin="round"/>
              </svg>
              {{ character.favoriteCount }} 收藏
            </span>
          </div>
        </div>

        <!-- 开场白气泡预览 -->
        <div class="greeting-section">
          <p class="section-label">开场白</p>
          <div class="greeting-bubble">
            {{ character.greeting }}
          </div>
        </div>

        <!-- 悬浮操作栏 -->
        <div class="action-bar">
          <button
            class="fav-action"
            type="button"
            :class="{ active: character.favorited }"
            @click="toggleFavorite"
          >
            <svg width="16" height="16" viewBox="0 0 16 16" :fill="character.favorited ? 'currentColor' : 'none'">
              <path d="M8 13.5L2.5 8.5C1 7 1 4.8 2.5 3.5C3.9 2.2 6 2.5 7 4L8 5.3L9 4C10 2.5 12.1 2.2 13.5 3.5C15 4.8 15 7 13.5 8.5L8 13.5Z" stroke="currentColor" stroke-width="1.4" stroke-linejoin="round"/>
            </svg>
            <span>{{ character.favorited ? '已收藏' : '收藏' }}</span>
          </button>
          <button class="chat-action" type="button" @click="startChat">
            <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
              <path d="M2 4C2 3 3 2 4 2H12C13 2 14 3 14 4V10C14 11 13 12 12 12H7L4 14V12H4C3 12 2 11 2 10V4Z" stroke="currentColor" stroke-width="1.4" stroke-linejoin="round"/>
            </svg>
            <span>开始聊天</span>
          </button>
        </div>
      </div>
    </template>

    <!-- 错误提示 -->
    <p v-if="errorText" class="toast">{{ errorText }}</p>
  </div>
</template>

<style scoped>
.page {
  min-height: 100vh;
  background: var(--bg-base);
  position: relative;
  overflow: hidden;
}

.state-hint {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 120px;
  color: var(--text-muted);
  font-size: 13px;
}

/* ---------- 背景模糊 ---------- */
.bg-blur {
  position: absolute;
  top: -40px;
  left: -40px;
  right: -40px;
  height: 320px;
  background-size: cover;
  background-position: center;
  background-color: var(--brand-from);
  filter: blur(40px) saturate(1.4);
  opacity: 0.4;
  z-index: 0;
}
.bg-mask {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 320px;
  background: linear-gradient(180deg, rgba(12, 16, 32, 0.3) 0%, rgba(12, 16, 32, 0.95) 100%);
  z-index: 1;
}

/* ---------- 返回 ---------- */
.back-btn {
  position: relative;
  z-index: 2;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  margin: 16px 0 0 24px;
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 999px;
  background: rgba(12, 16, 32, 0.5);
  backdrop-filter: blur(var(--blur-glass));
  color: var(--text-secondary);
  font-size: 13px;
  cursor: pointer;
  transition: all var(--duration-base) var(--ease-base);
}
.back-btn:hover { background: var(--bg-hover); color: var(--text-primary); }

/* ---------- 主内容 ---------- */
.main {
  position: relative;
  z-index: 2;
  max-width: 640px;
  margin: 0 auto;
  padding: 0 24px 120px;
}

.hero {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  padding: 20px 0 32px;
}
.avatar {
  width: 96px;
  height: 96px;
  border-radius: 28px;
  background: var(--brand-gradient);
  background-size: cover;
  background-position: center;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 40px;
  font-weight: 600;
  margin-bottom: 16px;
  box-shadow: 0 12px 32px rgba(94, 234, 212, 0.2);
}
.name {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  margin: 0 0 8px;
  font-size: 22px;
  font-weight: 600;
  color: var(--text-primary);
}
.official-tag {
  padding: 2px 8px;
  border-radius: 4px;
  background: rgba(94, 234, 212, 0.15);
  color: var(--brand-to);
  font-size: 11px;
  font-weight: 600;
}
.intro {
  margin: 0 0 14px;
  font-size: 13px;
  color: var(--text-secondary);
  line-height: 1.6;
  max-width: 420px;
}
.stats {
  display: flex;
  gap: 16px;
}
.stat-item {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--text-muted);
}

/* ---------- 开场白 ---------- */
.greeting-section {
  margin-bottom: 32px;
}
.section-label {
  margin: 0 0 10px;
  font-size: 11px;
  font-weight: 600;
  color: var(--text-muted);
  letter-spacing: 0.04em;
}
.greeting-bubble {
  padding: 16px 18px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.06);
  font-size: 14px;
  color: var(--text-primary);
  line-height: 1.7;
  position: relative;
}
.greeting-bubble::before {
  content: '';
  position: absolute;
  top: -6px;
  left: 24px;
  width: 12px;
  height: 12px;
  background: rgba(255, 255, 255, 0.04);
  border-left: 1px solid rgba(255, 255, 255, 0.06);
  border-top: 1px solid rgba(255, 255, 255, 0.06);
  transform: rotate(45deg);
}

/* ---------- 悬浮操作栏 ---------- */
.action-bar {
  position: fixed;
  bottom: 24px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 30;
  display: flex;
  gap: 12px;
  padding: 10px;
  border-radius: 999px;
  background: rgba(12, 16, 32, 0.85);
  backdrop-filter: blur(var(--blur-glass));
  border: 1px solid rgba(255, 255, 255, 0.08);
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.4);
}
.fav-action, .chat-action {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 20px;
  border: none;
  border-radius: 999px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all var(--duration-base) var(--ease-base);
}
.fav-action {
  background: rgba(255, 138, 101, 0.1);
  color: #ff8a65;
}
.fav-action:hover { background: rgba(255, 138, 101, 0.2); }
.fav-action.active { background: rgba(255, 138, 101, 0.25); }
.chat-action {
  background: var(--brand-gradient);
  color: white;
}
.chat-action:hover { filter: brightness(1.1); }

/* ---------- Toast ---------- */
.toast {
  position: fixed;
  bottom: 90px;
  left: 50%;
  transform: translateX(-50%);
  padding: 10px 20px;
  border-radius: 8px;
  background: rgba(239, 68, 68, 0.15);
  border: 1px solid rgba(239, 68, 68, 0.3);
  color: #fca5a5;
  font-size: 13px;
  z-index: 60;
}
</style>
