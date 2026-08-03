import { ref } from 'vue'
import { defineStore } from 'pinia'
import { statsApi } from '@/api/modules/stats'
import type { UsageStatsVO } from '@/types/api'

/**
 * 用量统计 store（M2 Token 统计）
 *
 * 面板打开时按需加载, 不在 onMounted 预拉, 避免无谓请求;
 * 每次发送消息完成后可手动 refresh, 让用户看到数字实时跳动
 */
export const useStatsStore = defineStore('stats', () => {
  const data = ref<UsageStatsVO | null>(null)
  const loading = ref(false)
  /** 面板是否展开 */
  const panelOpen = ref(false)

  async function load(): Promise<void> {
    loading.value = true
    try {
      data.value = await statsApi.usage()
    } finally {
      loading.value = false
    }
  }

  /** 打开面板并拉取最新数据 */
  async function openPanel(): Promise<void> {
    panelOpen.value = true
    await load()
  }

  function closePanel(): void {
    panelOpen.value = false
  }

  return { data, loading, panelOpen, load, openPanel, closePanel }
})
