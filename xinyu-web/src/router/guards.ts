import type { Router } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

/**
 * 全局路由守卫（契约三 3.3）
 *
 * requiresAuth 路由未登录 → 跳登录页并携带 redirect 回跳参数;
 * 已登录访问 /login → 直接进聊天页（避免重复登录）
 */
export function setupRouterGuards(router: Router): void {
  router.beforeEach((to) => {
    const auth = useAuthStore()

    if (to.meta.requiresAuth && !auth.isLoggedIn) {
      return { path: '/login', query: { redirect: to.fullPath } }
    }
    if (to.path === '/login' && auth.isLoggedIn) {
      return { path: '/chat' }
    }
  })
}
