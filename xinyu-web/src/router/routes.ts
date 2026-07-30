import type { RouteRecordRaw } from 'vue-router'
import { tokenStorage } from '@/utils/storage'

/**
 * 路由表 (设计文档 4.2 / 契约三 3.3)
 * M1-4.2 接入: /login 登录注册页 + /chat 聊天页(占位) + 根路径按登录态分流
 */
export const routes: RouteRecordRaw[] = [
  {
    path: '/',
    // 用 tokenStorage 而非 store 判断: redirect 回调不依赖 pinia 初始化时序
    // M2 有角色广场后, 游客落地页改为 /square
    redirect: () => (tokenStorage.get() ? '/chat' : '/login'),
  },
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/LoginView.vue'),
  },
  {
    path: '/chat',
    name: 'chat',
    meta: { requiresAuth: true },
    // M1-4.2 为验证守卫的占位实现, M1-4.3 替换为三栏聊天界面
    component: () => import('@/views/ChatView.vue'),
  },
]
