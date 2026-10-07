import { defineConfig } from 'vitest/config'
import { fileURLToPath, URL } from 'node:url'

/**
 * 前端单测配置 (vitest)
 *
 * 只跑纯逻辑层 (stores / composables / utils), 不引入组件渲染测试:
 * 这里要保护的是幂等、回滚、竞态这些「写错就产生脏数据」的逻辑,
 * 组件样式与交互留给人工验证, 避免测试维护成本压过收益。
 */
export default defineConfig({
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  test: {
    environment: 'happy-dom',
    include: ['src/**/*.spec.ts'],
    globals: true,
  },
})
