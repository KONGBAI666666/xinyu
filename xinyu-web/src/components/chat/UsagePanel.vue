<script setup lang="ts">
/**
 * 用量统计面板（M2 Token 统计）
 *
 * 右侧滑出 Drawer, TRAE 风暗黑毛玻璃; 入口由 ChatHeader 触发。
 * 数据按需加载: 打开面板时拉一次, 发送消息完成后由 ChatView 调 load 刷新。
 */
import { computed } from 'vue'
import { useStatsStore } from '@/stores/stats'

const statsStore = useStatsStore()

const data = computed(() => statsStore.data)

/** 千分位格式化, 无数据时返回占位符 */
function fmt(n: number | undefined, fallback = '—'): string {
  if (n == null) return fallback
  return n.toLocaleString('en-US')
}

/** 成本保留 4 位小数, 去掉末尾 0 */
function fmtCost(n: number | undefined): string {
  if (n == null) return '—'
  return n.toFixed(4).replace(/0+$/, '').replace(/\.$/, '')
}
</script>

<template>
  <Teleport to="body">
    <Transition name="fade">
      <div v-if="statsStore.panelOpen" class="mask" @click="statsStore.closePanel()" />
    </Transition>
    <Transition name="slide">
      <aside v-if="statsStore.panelOpen" class="panel">
        <!-- 顶栏 -->
        <header class="panel-header">
          <div class="header-left">
            <h2 class="panel-title">用量统计</h2>
            <span v-if="data" class="panel-date">{{ data.date }}</span>
          </div>
          <button class="close-btn" type="button" @click="statsStore.closePanel()">
            <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
              <path
                d="M1 1L13 13M13 1L1 13"
                stroke="currentColor"
                stroke-width="1.6"
                stroke-linecap="round"
              />
            </svg>
          </button>
        </header>

        <!-- 加载态 -->
        <div v-if="statsStore.loading && !data" class="state-hint">加载中…</div>
        <div v-else-if="!data" class="state-hint">暂无数据</div>

        <!-- 数据区 -->
        <div v-else class="panel-body">
          <!-- 今日 -->
          <section class="group">
            <div class="group-head">
              <span class="group-label today-label">今日</span>
              <span class="group-cost">
                <span class="cost-symbol">¥</span>{{ fmtCost(data.todayCost) }}
              </span>
            </div>
            <div class="metric-grid">
              <div class="metric">
                <div class="metric-value tabular-nums">{{ fmt(data.todayCallCount) }}</div>
                <div class="metric-label">调用次数</div>
              </div>
              <div class="metric">
                <div class="metric-value tabular-nums">{{ fmt(data.todayPromptTokens) }}</div>
                <div class="metric-label">输入 Token</div>
              </div>
              <div class="metric">
                <div class="metric-value tabular-nums">{{ fmt(data.todayCompletionTokens) }}</div>
                <div class="metric-label">输出 Token</div>
              </div>
            </div>
          </section>

          <!-- 累计 -->
          <section class="group">
            <div class="group-head">
              <span class="group-label total-label">累计</span>
              <span class="group-cost">
                <span class="cost-symbol">¥</span>{{ fmtCost(data.totalCost) }}
              </span>
            </div>
            <div class="metric-grid">
              <div class="metric">
                <div class="metric-value tabular-nums">{{ fmt(data.totalCallCount) }}</div>
                <div class="metric-label">调用次数</div>
              </div>
              <div class="metric">
                <div class="metric-value tabular-nums">{{ fmt(data.totalPromptTokens) }}</div>
                <div class="metric-label">输入 Token</div>
              </div>
              <div class="metric">
                <div class="metric-value tabular-nums">{{ fmt(data.totalCompletionTokens) }}</div>
                <div class="metric-label">输出 Token</div>
              </div>
            </div>
          </section>

          <!-- 合计 Token -->
          <section class="summary">
            <div class="summary-row">
              <span class="summary-label">今日合计</span>
              <span class="summary-value tabular-nums">
                {{ fmt(data.todayPromptTokens + data.todayCompletionTokens) }} tokens
              </span>
            </div>
            <div class="summary-row">
              <span class="summary-label">累计合计</span>
              <span class="summary-value tabular-nums">
                {{ fmt(data.totalPromptTokens + data.totalCompletionTokens) }} tokens
              </span>
            </div>
          </section>

          <p class="footnote">
            成本基于通义千问 qwen-plus 公开单价估算, 仅供参考, 实际计费以供应商账单为准。
          </p>
        </div>
      </aside>
    </Transition>
  </Teleport>
</template>

<style scoped>
/* ---------- 遮罩 ---------- */
.mask {
  position: fixed;
  inset: 0;
  z-index: 40;
  background: rgba(0, 0, 0, 0.4);
  backdrop-filter: blur(2px);
}

/* ---------- 面板 ---------- */
.panel {
  position: fixed;
  top: 0;
  right: 0;
  bottom: 0;
  z-index: 50;
  width: 400px;
  max-width: 90vw;
  display: flex;
  flex-direction: column;
  background: rgba(12, 16, 32, 0.85);
  backdrop-filter: blur(var(--blur-glass));
  border-left: 1px solid rgba(255, 255, 255, 0.06);
  box-shadow: -20px 0 60px rgba(0, 0, 0, 0.4);
}

/* ---------- 顶栏 ---------- */
.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px 24px 16px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
}

.header-left {
  display: flex;
  align-items: baseline;
  gap: 12px;
}

.panel-title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
}

.panel-date {
  font-size: 12px;
  color: var(--text-muted);
  font-variant-numeric: tabular-nums;
}

.close-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: var(--text-muted);
  cursor: pointer;
  transition: all var(--duration-base) var(--ease-base);
}

.close-btn:hover {
  background: var(--bg-hover);
  color: var(--text-primary);
}

/* ---------- 状态提示 ---------- */
.state-hint {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-muted);
  font-size: 13px;
}

/* ---------- 数据区 ---------- */
.panel-body {
  flex: 1;
  overflow-y: auto;
  padding: 20px 24px 24px;
}

/* ---------- 分组 ---------- */
.group {
  margin-bottom: 24px;
}

.group-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}

.group-label {
  font-size: 12px;
  font-weight: 500;
  letter-spacing: 0.04em;
  padding: 3px 10px;
  border-radius: 6px;
}

.today-label {
  background: linear-gradient(135deg, rgba(139, 124, 246, 0.18), rgba(94, 234, 212, 0.18));
  color: var(--brand-to);
}

.total-label {
  background: rgba(255, 255, 255, 0.06);
  color: var(--text-secondary);
}

.group-cost {
  font-size: 22px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  background: var(--brand-gradient);
  background-clip: text;
  -webkit-background-clip: text;
  color: transparent;
}

.cost-symbol {
  font-size: 14px;
  font-weight: 500;
  margin-right: 2px;
  opacity: 0.8;
}

/* ---------- 指标网格 ---------- */
.metric-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
}

.metric {
  padding: 14px 10px;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.04);
  text-align: center;
  transition: all var(--duration-base) var(--ease-base);
}

.metric:hover {
  background: rgba(255, 255, 255, 0.05);
  border-color: rgba(255, 255, 255, 0.08);
}

.metric-value {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
  line-height: 1.2;
}

.metric-label {
  margin-top: 4px;
  font-size: 11px;
  color: var(--text-muted);
}

/* ---------- 汇总 ---------- */
.summary {
  margin-top: 8px;
  padding: 14px 16px;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.02);
  border: 1px solid rgba(255, 255, 255, 0.04);
}

.summary-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 5px 0;
}

.summary-label {
  font-size: 12px;
  color: var(--text-secondary);
}

.summary-value {
  font-size: 13px;
  font-weight: 500;
  color: var(--text-primary);
}

.footnote {
  margin: 20px 0 0;
  font-size: 11px;
  line-height: 1.6;
  color: var(--text-muted);
}

/* ---------- 动画 ---------- */
.fade-enter-active,
.fade-leave-active {
  transition: opacity var(--duration-base) var(--ease-base);
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

.slide-enter-active,
.slide-leave-active {
  transition: transform var(--duration-base) var(--ease-base);
}
.slide-enter-from,
.slide-leave-to {
  transform: translateX(100%);
}
</style>
