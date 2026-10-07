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
    // M2.3: 游客落地页改为 /square, 已登录进 /chat
    redirect: () => (tokenStorage.get() ? '/chat' : '/square'),
  },
  {
    path: '/square',
    name: 'square',
    // 游客可逛广场, 不设 requiresAuth
    component: () => import('@/views/SquareView.vue'),
  },
  {
    path: '/square/:id',
    name: 'character-detail',
    // 游客可看角色详情, 不设 requiresAuth
    component: () => import('@/views/CharacterDetailView.vue'),
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
    component: () => import('@/views/ChatView.vue'),
  },
  {
    path: '/settings/models',
    name: 'models',
    meta: { requiresAuth: true },
    component: () => import('@/views/ModelsView.vue'),
  },
  {
    path: '/settings/memories',
    name: 'memories',
    meta: { requiresAuth: true },
    component: () => import('@/views/MemoriesView.vue'),
  },
  {
    path: '/settings/characters',
    name: 'characters',
    meta: { requiresAuth: true },
    component: () => import('@/views/CharactersView.vue'),
  },
  {
    path: '/settings/knowledge-bases',
    name: 'knowledge-bases',
    meta: { requiresAuth: true },
    component: () => import('@/views/KnowledgeBaseView.vue'),
  },
]
