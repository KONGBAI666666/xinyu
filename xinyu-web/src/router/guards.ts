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

    // token 已过期: 清掉本地凭证, 按未登录处理（不等首个请求 401 才跳转）
    if (auth.isLoggedIn && !auth.isTokenValid()) {
      auth.logout()
    }

    if (to.meta.requiresAuth && !auth.isLoggedIn) {
      return { path: '/login', query: { redirect: to.fullPath } }
    }
    // 管理后台: 登录之外还要求 role=ADMIN (本地缓存缺 role 时按非管理员处理)
    if (to.meta.requiresAdmin && auth.user?.role !== 'ADMIN') {
      return { path: '/chat' }
    }
    if (to.path === '/login' && auth.isLoggedIn) {
      return { path: '/chat' }
    }
  })
}
