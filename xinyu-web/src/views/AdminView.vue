<script setup lang="ts">
/**
 * 管理后台 (role=ADMIN, 路由守卫 + 后端 require_admin 双重把守)
 *
 * 三个标签:
 * - 平台概览: 全站消息量 / token 消耗
 * - 用户管理: 按用户名精确查找 → 封禁/解封 (v1 无分页列表)
 * - 角色审核: 全量角色分页 (可按状态过滤) → 通过发布 / 下架
 *
 * 风格: 与 ModelsView/MemoriesView 一致的 TRAE 暗黑系
 */
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { adminApi } from '@/api/modules/admin'
import { BizError } from '@/utils/BizError'
import type { AdminCharacterVO, AdminUserVO, PlatformOverviewVO } from '@/types/api'

const router = useRouter()

const tab = ref<'overview' | 'users' | 'characters'>('overview')
const loading = ref(false)
const errorText = ref('')

function toast(msg: string): void {
  errorText.value = msg
  setTimeout(() => (errorText.value = ''), 3000)
}

function fallback(e: unknown, text: string): string {
  return e instanceof BizError ? e.message : text
}

// ---------- 平台概览 ----------
const overview = ref<PlatformOverviewVO | null>(null)

async function loadOverview(): Promise<void> {
  loading.value = true
  try {
    overview.value = await adminApi.overview()
  } catch (e) {
    toast(fallback(e, '加载概览失败'))
  } finally {
    loading.value = false
  }
}

// ---------- 用户管理 ----------
const searchUsername = ref('')
const foundUser = ref<AdminUserVO | null>(null)
const userSearching = ref(false)

async function searchUser(): Promise<void> {
  const name = searchUsername.value.trim()
  if (!name) return
  userSearching.value = true
  foundUser.value = null
  try {
    foundUser.value = await adminApi.findUser(name)
    if (!foundUser.value) toast('未找到该用户')
  } catch (e) {
    toast(fallback(e, '查找失败'))
  } finally {
    userSearching.value = false
  }
}

async function toggleUserStatus(user: AdminUserVO): Promise<void> {
  const next = user.status === 'BANNED' ? 'ACTIVE' : 'BANNED'
  try {
    foundUser.value = await adminApi.setUserStatus(user.id, next)
    toast(next === 'BANNED' ? '已封禁' : '已解封')
  } catch (e) {
    toast(fallback(e, '操作失败'))
  }
}

// ---------- 角色审核 ----------
const REVIEW_PAGE_SIZE = 30
const reviewStatusFilter = ref<'ALL' | 'DRAFT' | 'PENDING' | 'PUBLISHED' | 'OFFLINE'>('ALL')
const reviewList = ref<AdminCharacterVO[]>([])
const reviewLoading = ref(false)
const reviewHasMore = ref(false)
/** 每个状态过滤条件独立的翻页游标 */
const reviewOffsets: Record<string, number> = {}

async function loadReviews(reset: boolean): Promise<void> {
  if (reviewLoading.value) return
  reviewLoading.value = true
  try {
    const status = reviewStatusFilter.value === 'ALL' ? null : reviewStatusFilter.value
    const key = reviewStatusFilter.value
    if (reset) {
      reviewOffsets[key] = 0
      reviewList.value = []
    }
    const page = await adminApi.listCharacters(status, reviewOffsets[key] ?? 0, REVIEW_PAGE_SIZE)
    reviewList.value = reset ? page : [...reviewList.value, ...page]
    reviewOffsets[key] = (reviewOffsets[key] ?? 0) + page.length
    reviewHasMore.value = page.length >= REVIEW_PAGE_SIZE
  } catch (e) {
    toast(fallback(e, '加载审核列表失败'))
  } finally {
    reviewLoading.value = false
  }
}

async function review(character: AdminCharacterVO, status: 'PUBLISHED' | 'OFFLINE'): Promise<void> {
  try {
    const updated = await adminApi.reviewCharacter(character.id, status)
    const idx = reviewList.value.findIndex((c) => c.id === updated.id)
    if (idx >= 0) reviewList.value[idx] = updated
    toast(status === 'PUBLISHED' ? '已通过发布' : '已下架')
  } catch (e) {
    toast(fallback(e, '审核操作失败'))
  }
}

function switchTab(next: 'overview' | 'users' | 'characters'): void {
  tab.value = next
  if (next === 'overview' && !overview.value) loadOverview()
  if (next === 'characters' && reviewList.value.length === 0) loadReviews(true)
}

onMounted(loadOverview)

const statusLabel: Record<string, string> = {
  DRAFT: '草稿',
  PENDING: '待审',
  PUBLISHED: '已发布',
  OFFLINE: '已下架',
}
</script>

<template>
  <div class="admin-page">
    <header class="admin-header">
      <button type="button" class="back-btn" @click="router.push('/chat')">← 返回聊天</button>
      <h1 class="admin-title">管理后台</h1>
      <div class="tabs">
        <button
          v-for="t in (['overview', 'users', 'characters'] as const)"
          :key="t"
          type="button"
          class="tab-btn"
          :class="{ active: tab === t }"
          @click="switchTab(t)"
        >
          {{ t === 'overview' ? '平台概览' : t === 'users' ? '用户管理' : '角色审核' }}
        </button>
      </div>
    </header>

    <!-- 平台概览 -->
    <section v-if="tab === 'overview'" class="panel">
      <div v-if="loading" class="hint">加载中…</div>
      <template v-else-if="overview">
        <div class="metric-grid">
          <div class="metric-card">
            <p class="metric-value">{{ overview.messageCount }}</p>
            <p class="metric-label">全站生成消息数</p>
          </div>
          <div class="metric-card">
            <p class="metric-value">{{ overview.totalPromptTokens.toLocaleString() }}</p>
            <p class="metric-label">输入 Token 总消耗</p>
          </div>
          <div class="metric-card">
            <p class="metric-value">{{ overview.totalCompletionTokens.toLocaleString() }}</p>
            <p class="metric-label">输出 Token 总消耗</p>
          </div>
        </div>
      </template>
    </section>

    <!-- 用户管理 -->
    <section v-else-if="tab === 'users'" class="panel">
      <div class="search-row">
        <input
          v-model="searchUsername"
          class="search-input"
          placeholder="输入完整用户名精确查找"
          maxlength="32"
          @keyup.enter="searchUser"
        />
        <button type="button" class="primary-btn" :disabled="userSearching" @click="searchUser">
          {{ userSearching ? '查找中…' : '查找' }}
        </button>
      </div>
      <div v-if="foundUser" class="user-card">
        <div class="user-info">
          <p class="user-name">{{ foundUser.nickname }} <span class="user-username">@{{ foundUser.username }}</span></p>
          <p class="user-meta">
            {{ foundUser.role }} · {{ foundUser.status === 'BANNED' ? '已封禁' : '正常' }}
            <template v-if="foundUser.email"> · {{ foundUser.email }}</template>
          </p>
        </div>
        <button
          type="button"
          class="primary-btn"
          :class="{ danger: foundUser.status !== 'BANNED' }"
          @click="toggleUserStatus(foundUser)"
        >
          {{ foundUser.status === 'BANNED' ? '解封' : '封禁' }}
        </button>
      </div>
    </section>

    <!-- 角色审核 -->
    <section v-else class="panel">
      <div class="filter-row">
        <button
          v-for="s in (['ALL', 'DRAFT', 'PENDING', 'PUBLISHED', 'OFFLINE'] as const)"
          :key="s"
          type="button"
          class="filter-btn"
          :class="{ active: reviewStatusFilter === s }"
          @click="reviewStatusFilter = s; loadReviews(true)"
        >
          {{ s === 'ALL' ? '全部' : statusLabel[s] }}
        </button>
      </div>
      <div class="review-list">
        <div v-for="c in reviewList" :key="c.id" class="review-card">
          <div class="review-info">
            <p class="review-name">
              {{ c.name }}
              <span class="status-badge" :class="`st-${c.status}`">{{ statusLabel[c.status] ?? c.status }}</span>
            </p>
            <p class="review-meta">{{ c.intro || '（无介绍）' }}</p>
            <p class="review-meta">创作者 {{ c.creatorType === 'OFFICIAL' ? '官方' : c.creatorId }} · 对话 {{ c.chatCount }} · 收藏 {{ c.favoriteCount }}</p>
          </div>
          <div class="review-actions">
            <button
              v-if="c.status !== 'PUBLISHED'"
              type="button"
              class="primary-btn"
              @click="review(c, 'PUBLISHED')"
            >通过发布</button>
            <button
              v-if="c.status !== 'OFFLINE'"
              type="button"
              class="primary-btn danger"
              @click="review(c, 'OFFLINE')"
            >下架</button>
          </div>
        </div>
        <p v-if="!reviewLoading && reviewList.length === 0" class="hint">暂无角色</p>
        <button
          v-if="reviewHasMore"
          type="button"
          class="more-btn"
          :disabled="reviewLoading"
          @click="loadReviews(false)"
        >
          {{ reviewLoading ? '加载中…' : '加载更多' }}
        </button>
      </div>
    </section>

    <p v-if="errorText" class="toast">{{ errorText }}</p>
  </div>
</template>

<style scoped>
.admin-page {
  max-width: 880px;
  margin: 0 auto;
  padding: 24px 20px 48px;
}

.admin-header {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 20px;
}

.back-btn {
  padding: 6px 12px;
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: var(--radius-btn);
  background: transparent;
  color: var(--text-secondary);
  font-size: 13px;
  cursor: pointer;
}
.back-btn:hover {
  background: var(--bg-hover);
}

.admin-title {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
  flex: 1;
}

.tabs {
  display: flex;
  gap: 6px;
}
.tab-btn {
  padding: 6px 14px;
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: var(--radius-btn);
  background: transparent;
  color: var(--text-secondary);
  font-size: 13px;
  cursor: pointer;
}
.tab-btn.active {
  background: var(--brand-gradient);
  border-color: transparent;
  color: #fff;
}

.panel {
  min-height: 300px;
}

.hint {
  padding: 40px 0;
  text-align: center;
  color: var(--text-muted);
  font-size: 13px;
}

/* 概览 */
.metric-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 14px;
}
.metric-card {
  padding: 20px;
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 12px;
  background: var(--bg-elevated);
}
.metric-value {
  margin: 0 0 6px;
  font-size: 26px;
  font-weight: 700;
  color: var(--text-primary);
  font-variant-numeric: tabular-nums;
}
.metric-label {
  margin: 0;
  font-size: 12px;
  color: var(--text-muted);
}

/* 用户管理 */
.search-row {
  display: flex;
  gap: 10px;
  margin-bottom: 16px;
}
.search-input {
  flex: 1;
  padding: 9px 14px;
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: var(--radius-btn);
  background: rgba(0, 0, 0, 0.25);
  color: var(--text-primary);
  font-size: 13px;
  outline: none;
}
.search-input:focus {
  border-color: rgba(94, 234, 212, 0.4);
}
.user-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px;
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 12px;
  background: var(--bg-elevated);
}
.user-name {
  margin: 0 0 4px;
  font-size: 14px;
  color: var(--text-primary);
}
.user-username {
  color: var(--text-muted);
  font-size: 12px;
}
.user-meta {
  margin: 0;
  font-size: 12px;
  color: var(--text-muted);
}

.primary-btn {
  padding: 7px 16px;
  border: none;
  border-radius: var(--radius-btn);
  background: var(--brand-gradient);
  color: #fff;
  font-size: 13px;
  cursor: pointer;
}
.primary-btn.danger {
  background: rgba(248, 113, 113, 0.15);
  color: #fca5a5;
}
.primary-btn:disabled {
  opacity: 0.55;
}

/* 角色审核 */
.filter-row {
  display: flex;
  gap: 8px;
  margin-bottom: 14px;
  flex-wrap: wrap;
}
.filter-btn {
  padding: 5px 12px;
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 999px;
  background: transparent;
  color: var(--text-secondary);
  font-size: 12px;
  cursor: pointer;
}
.filter-btn.active {
  background: rgba(94, 234, 212, 0.1);
  border-color: rgba(94, 234, 212, 0.3);
  color: var(--brand-to);
}

.review-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding: 14px 16px;
  margin-bottom: 10px;
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 12px;
  background: var(--bg-elevated);
}
.review-name {
  margin: 0 0 4px;
  font-size: 14px;
  color: var(--text-primary);
  display: flex;
  align-items: center;
  gap: 8px;
}
.status-badge {
  padding: 1px 8px;
  border-radius: 999px;
  font-size: 10px;
  background: rgba(255, 255, 255, 0.08);
  color: var(--text-muted);
}
.status-badge.st-PUBLISHED {
  background: rgba(94, 234, 212, 0.12);
  color: var(--brand-to);
}
.status-badge.st-OFFLINE {
  background: rgba(248, 113, 113, 0.12);
  color: #fca5a5;
}
.review-meta {
  margin: 0 0 2px;
  font-size: 12px;
  color: var(--text-muted);
}
.review-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

.more-btn {
  display: block;
  margin: 14px auto 0;
  padding: 7px 20px;
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: var(--radius-btn);
  background: transparent;
  color: var(--text-secondary);
  font-size: 13px;
  cursor: pointer;
}
.more-btn:hover {
  background: var(--bg-hover);
}

.toast {
  position: fixed;
  bottom: 28px;
  left: 50%;
  transform: translateX(-50%);
  padding: 8px 18px;
  border-radius: 8px;
  background: rgba(20, 24, 40, 0.95);
  border: 1px solid rgba(255, 255, 255, 0.12);
  color: var(--text-primary);
  font-size: 13px;
}
</style>
