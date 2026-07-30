import { createRouter, createWebHistory } from 'vue-router'
import { routes } from './routes'
import { setupRouterGuards } from './guards'

/**
 * 路由实例
 * 守卫逻辑 (requiresAuth) 在 guards.ts 中集中维护
 */
const router = createRouter({
  history: createWebHistory(),
  routes,
})

setupRouterGuards(router)

export default router
