<script setup lang="ts">
/**
 * 角色广场（M2.3）
 *
 * 布局:
 * - 顶栏: 品牌 + 搜索框 + 登录/进入聊天入口
 * - 排序 tab: 推荐 / 热门 / 最新
 * - 卡片网格: 头像 + 名称 + intro + 对话/收藏数 + 收藏按钮 + 进入详情
 *
 * 游客可访问, 点收藏/开聊引导登录（带回跳）。
 */
import { onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { charactersApi, type SquareSort } from '@/api/modules/character'
import { useAuthStore } from '@/stores/auth'
import { BizError } from '@/utils/BizError'
import type { CharacterVO } from '@/types/api'

const router = useRouter()
const authStore = useAuthStore()

const list = ref<CharacterVO[]>([])
const loading = ref(false)
const errorText = ref('')

const keyword = ref('')
const sort = ref<SquareSort>('RECOMMEND')

/** 搜索防抖: 输入停顿 300ms 后触发 */
let debounceTimer: ReturnType<typeof setTimeout> | null = null

onMounted(() => {
  load()
})

watch(keyword, () => {
  if (debounceTimer) clearTimeout(debounceTimer)
  debounceTimer = setTimeout(load, 300)
})

watch(sort, () => load())

async function load(): Promise<void> {
  loading.value = true
  errorText.value = ''
  try {
    list.value = await charactersApi.square(keyword.value.trim() || null, sort.value)
  } catch (e) {
    errorText.value = e instanceof BizError ? e.message : '加载失败'
  } finally {
    loading.value = false
  }
}

async function toggleFavorite(c: CharacterVO): Promise<void> {
  // 游客引导登录
  if (!authStore.isLoggedIn) {
    redirectToLogin()
    return
  }
  try {
    if (c.favorited) {
      await charactersApi.unfavorite(c.id)
      c.favorited = false
      c.favoriteCount = Math.max(0, c.favoriteCount - 1)
    } else {
      await charactersApi.favorite(c.id)
      c.favorited = true
      c.favoriteCount += 1
    }
  } catch (e) {
    errorText.value = e instanceof BizError ? e.message : '操作失败'
  }
}

function startChat(c: CharacterVO): void {
  // 游客引导登录
  if (!authStore.isLoggedIn) {
    redirectToLogin()
    return
  }
  router.push({ path: '/chat', query: { new: c.id } })
}

function redirectToLogin(): void {
  router.push({ path: '/login', query: { redirect: '/square' } })
}

const sortTabs: { value: SquareSort; label: string }[] = [
  { value: 'RECOMMEND', label: '推荐' },
  { value: 'HOT', label: '热门' },
  { value: 'LATEST', label: '最新' },
]
</script>

<template>
  <div class="page">
    <!-- 顶栏 -->
    <header class="topbar">
      <h1 class="brand">心屿</h1>

      <div class="search-box">
        <svg class="search-icon" width="14" height="14" viewBox="0 0 14 14" fill="none">
          <circle cx="6" cy="6" r="4.5" stroke="currentColor" stroke-width="1.4"/>
          <path d="M9.5 9.5L12 12" stroke="currentColor" stroke-width="1.4" stroke-linecap="round"/>
        </svg>
        <input
          v-model="keyword"
          class="search-input"
          type="text"
          placeholder="搜索角色名或介绍"
        />
      </div>

      <div class="top-actions">
        <button v-if="!authStore.isLoggedIn" class="ghost-btn" type="button" @click="redirectToLogin">
          登录
        </button>
        <button v-else class="ghost-btn" type="button" @click="router.push('/chat')">
          进入聊天
        </button>
      </div>
    </header>

    <!-- 排序 tab -->
    <div class="sort-bar">
      <button
        v-for="t in sortTabs"
        :key="t.value"
        class="sort-tab"
        :class="{ active: sort === t.value }"
        type="button"
        @click="sort = t.value"
      >
        {{ t.label }}
      </button>
    </div>

    <!-- 内容 -->
    <div class="content">
      <!-- 加载态 -->
      <div v-if="loading" class="state-hint">加载中…</div>

      <!-- 空状态 -->
      <div v-else-if="list.length === 0" class="empty-state">
        <div class="empty-icon">
          <svg width="48" height="48" viewBox="0 0 48 48" fill="none">
            <circle cx="24" cy="24" r="20" stroke="currentColor" stroke-width="1.5" stroke-dasharray="4 3"/>
            <path d="M16 24H32M24 16V32" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
          </svg>
        </div>
        <p class="empty-text">{{ keyword ? '没有找到相关角色' : '广场还没有角色' }}</p>
        <p v-if="keyword" class="empty-hint">换个关键词试试</p>
        <p v-else class="empty-hint">创建一个角色发布到广场吧</p>
      </div>

      <!-- 卡片网格 -->
      <div v-else class="card-grid">
        <div
          v-for="c in list"
          :key="c.id"
          class="char-card"
          @click="router.push(`/square/${c.id}`)"
        >
          <div class="card-head">
            <div class="avatar" :style="c.avatarUrl ? `background-image:url(${c.avatarUrl})` : ''">
              <span v-if="!c.avatarUrl">{{ c.name.charAt(0) }}</span>
            </div>
            <div class="head-info">
              <div class="name-row">
                <span class="name">{{ c.name }}</span>
                <span v-if="c.creatorType === 'OFFICIAL'" class="official-tag">官方</span>
              </div>
              <span class="intro">{{ c.intro || '暂无介绍' }}</span>
            </div>
          </div>

          <div class="card-meta">
            <span class="meta-item">
              <svg width="11" height="11" viewBox="0 0 11 11" fill="none">
                <path d="M2 3.5H9V8C9 8.5 8.5 9 8 9H3C2.5 9 2 8.5 2 8V3.5Z" stroke="currentColor" stroke-width="0.9"/>
                <path d="M2 4L5.5 6.5L9 4" stroke="currentColor" stroke-width="0.9" stroke-linejoin="round"/>
              </svg>
              {{ c.chatCount }}
            </span>
            <span class="meta-item">
              <svg width="11" height="11" viewBox="0 0 11 11" fill="none">
                <path d="M5.5 9.5L1.8 6C0.5 4.8 0.5 3 1.8 1.8C3 0.7 4.7 1 5.5 2.2C6.3 1 8 0.7 9.2 1.8C10.5 3 10.5 4.8 9.2 6L5.5 9.5Z" stroke="currentColor" stroke-width="0.9" stroke-linejoin="round"/>
              </svg>
              {{ c.favoriteCount }}
            </span>
          </div>

          <div class="card-actions" @click.stop>
            <button
              class="fav-btn"
              type="button"
              :class="{ active: c.favorited }"
              :title="c.favorited ? '取消收藏' : '收藏'"
              @click="toggleFavorite(c)"
            >
              <svg width="14" height="14" viewBox="0 0 14 14" :fill="c.favorited ? 'currentColor' : 'none'">
                <path d="M7 12L2.2 7.5C0.9 6.2 0.9 4.2 2.2 3C3.4 1.9 5.2 2.1 6 3.3L7 4.7L8 3.3C8.8 2.1 10.6 1.9 11.8 3C13.1 4.2 13.1 6.2 11.8 7.5L7 12Z" stroke="currentColor" stroke-width="1.3" stroke-linejoin="round"/>
              </svg>
            </button>
            <button class="chat-btn" type="button" @click="startChat(c)">
              开聊
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- 错误提示 -->
    <p v-if="errorText" class="toast">{{ errorText }}</p>
  </div>
</template>

<style scoped>
.page {
  min-height: 100vh;
  background: var(--bg-base);
}

/* ---------- 顶栏 ---------- */
.topbar {
  display: flex;
  align-items: center;
  gap: 16px;
  height: 64px;
  padding: 0 32px;
  position: sticky;
  top: 0;
  z-index: 10;
  background: rgba(12, 16, 32, 0.85);
  backdrop-filter: blur(var(--blur-glass));
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
}
.brand {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
  background: var(--brand-gradient);
  background-clip: text;
  -webkit-background-clip: text;
  color: transparent;
  flex-shrink: 0;
}

.search-box {
  flex: 1;
  max-width: 480px;
  position: relative;
  display: flex;
  align-items: center;
}
.search-icon {
  position: absolute;
  left: 12px;
  color: var(--text-muted);
  pointer-events: none;
}
.search-input {
  width: 100%;
  padding: 8px 12px 8px 34px;
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 999px;
  background: rgba(0, 0, 0, 0.2);
  color: var(--text-primary);
  font-size: 13px;
  outline: none;
  box-sizing: border-box;
  transition: border-color var(--duration-base) var(--ease-base);
}
.search-input:focus { border-color: rgba(94, 234, 212, 0.4); }
.search-input::placeholder { color: var(--text-muted); }

.top-actions { display: flex; gap: 8px; }
.ghost-btn {
  padding: 6px 16px;
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: var(--radius-btn);
  background: transparent;
  color: var(--text-secondary);
  font-size: 13px;
  cursor: pointer;
  transition: all var(--duration-base) var(--ease-base);
}
.ghost-btn:hover {
  background: var(--bg-hover);
  color: var(--text-primary);
  border-color: rgba(94, 234, 212, 0.3);
}

/* ---------- 排序 tab ---------- */
.sort-bar {
  display: flex;
  gap: 4px;
  padding: 16px 32px 0;
  max-width: 1200px;
  margin: 0 auto;
}
.sort-tab {
  padding: 6px 16px;
  border: none;
  border-radius: 999px;
  background: transparent;
  color: var(--text-muted);
  font-size: 13px;
  cursor: pointer;
  transition: all var(--duration-base) var(--ease-base);
}
.sort-tab:hover { color: var(--text-secondary); }
.sort-tab.active {
  background: rgba(94, 234, 212, 0.1);
  color: var(--brand-to);
}

/* ---------- 内容 ---------- */
.content {
  max-width: 1200px;
  margin: 0 auto;
  padding: 20px 32px 40px;
}

.state-hint {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 80px;
  color: var(--text-muted);
  font-size: 13px;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 80px 24px;
  color: var(--text-muted);
}
.empty-icon { color: rgba(139, 124, 246, 0.3); margin-bottom: 16px; }
.empty-text { margin: 0 0 4px; font-size: 15px; color: var(--text-secondary); }
.empty-hint { margin: 0; font-size: 12px; }

/* ---------- 卡片网格 ---------- */
.card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 14px;
}
.char-card {
  padding: 16px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.06);
  cursor: pointer;
  transition: all var(--duration-base) var(--ease-base);
}
.char-card:hover {
  background: rgba(255, 255, 255, 0.05);
  border-color: rgba(94, 234, 212, 0.2);
  transform: translateY(-2px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.2);
}

.card-head {
  display: flex;
  gap: 12px;
  margin-bottom: 12px;
}
.avatar {
  width: 48px;
  height: 48px;
  border-radius: 14px;
  background: var(--brand-gradient);
  background-size: cover;
  background-position: center;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 20px;
  font-weight: 600;
  flex-shrink: 0;
}
.head-info { flex: 1; min-width: 0; }
.name-row {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 3px;
}
.name {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.official-tag {
  padding: 1px 6px;
  border-radius: 4px;
  background: rgba(94, 234, 212, 0.15);
  color: var(--brand-to);
  font-size: 10px;
  font-weight: 600;
  flex-shrink: 0;
}
.intro {
  font-size: 12px;
  color: var(--text-muted);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.card-meta {
  display: flex;
  gap: 12px;
  margin-bottom: 12px;
  font-size: 11px;
  color: var(--text-muted);
}
.meta-item {
  display: flex;
  align-items: center;
  gap: 3px;
}

.card-actions {
  display: flex;
  gap: 8px;
  padding-top: 10px;
  border-top: 1px solid rgba(255, 255, 255, 0.04);
}
.fav-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: var(--radius-btn);
  background: transparent;
  color: var(--text-muted);
  cursor: pointer;
  transition: all var(--duration-base) var(--ease-base);
}
.fav-btn:hover { background: var(--bg-hover); color: #ff8a65; }
.fav-btn.active { color: #ff8a65; border-color: rgba(255, 138, 101, 0.3); }

.chat-btn {
  flex: 1;
  border: none;
  border-radius: var(--radius-btn);
  background: var(--brand-gradient);
  color: white;
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  transition: all var(--duration-base) var(--ease-base);
}
.chat-btn:hover { filter: brightness(1.1); }

/* ---------- Toast ---------- */
.toast {
  position: fixed;
  bottom: 30px;
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
