<script setup lang="ts">
/**
 * 环境验证占位页
 * 用途: 确认 Vite + Vue3 + TS + Tailwind + Design Token 全链路可用
 * 该页面将在 M1-3 聊天页完成后移除
 */
const stack = ['Vue 3', 'TypeScript', 'Vite', 'Pinia', 'Vue Router', 'Tailwind CSS']
</script>

<template>
  <div class="flex h-full flex-col items-center justify-center gap-6">
    <!-- 品牌渐变 Logo 文字: 验证 tokens.css 变量生效 -->
    <h1 class="brand-title text-6xl font-bold">心屿</h1>
    <p class="text-lg" :style="{ color: 'var(--text-secondary)' }">
      每个人心里，都有一座岛。
    </p>

    <!-- 技术栈标签: 验证 Tailwind 工具类生效 -->
    <div class="flex flex-wrap justify-center gap-2">
      <span v-for="item in stack" :key="item" class="stack-tag text-sm">
        {{ item }}
      </span>
    </div>

    <p class="text-xs" :style="{ color: 'var(--text-muted)' }">
      M1-0 · 脚手架就绪，等待 common 基础设施接入
    </p>
  </div>
</template>

<style scoped>
.brand-title {
  background: var(--brand-gradient);
  background-clip: text;
  -webkit-background-clip: text;
  color: transparent;
}

.stack-tag {
  padding: 4px 12px;
  border-radius: var(--radius-btn);
  background: var(--bg-elevated);
  color: var(--text-secondary);
  backdrop-filter: blur(var(--blur-glass));
}
</style>
