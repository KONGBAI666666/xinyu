import type { RouteRecordRaw } from 'vue-router'

/**
 * 路由表 (设计文档 4.2)
 * M1-0 仅挂载环境验证占位页; 各业务页面随对应里程碑逐步接入
 */
export const routes: RouteRecordRaw[] = [
  {
    path: '/',
    name: 'welcome',
    // 占位页: 验证脚手架可运行, M1-3 聊天页完成后移除
    component: () => import('@/views/WelcomeView.vue'),
  },
]
