import { createRouter, createWebHistory } from 'vue-router'
import { routes } from './routes'

/**
 * 路由实例
 * 守卫逻辑 (requiresAuth / requiresAdmin) 在 M1-2 认证模块完成后于 guards.ts 中接入
 */
const router = createRouter({
  history: createWebHistory(),
  routes,
})

export default router
