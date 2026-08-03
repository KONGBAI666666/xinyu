import { ref } from 'vue'
import { defineStore } from 'pinia'
import { modelsApi } from '@/api/modules/models'
import type { AiModelVO } from '@/types/api'

/**
 * 模型库 store（用户级）
 *
 * 懒加载: 不在应用启动时预拉, 首次进入模型管理页或打开切换器时才加载。
 */
export const useModelsStore = defineStore('models', () => {
  const list = ref<AiModelVO[]>([])
  const loaded = ref(false)
  const loading = ref(false)

  async function load(force = false): Promise<void> {
    if (loaded.value && !force) return
    loading.value = true
    try {
      list.value = await modelsApi.list()
      loaded.value = true
    } finally {
      loading.value = false
    }
  }

  /** 当前默认模型（列表里 isDefault=1 的那个, 可能为 null） */
  function defaultModel(): AiModelVO | null {
    return list.value.find((m) => m.isDefault === 1) ?? null
  }

  /** 按 id 查模型 */
  function getById(id: string | null): AiModelVO | null {
    if (!id) return null
    return list.value.find((m) => m.id === id) ?? null
  }

  return { list, loaded, loading, load, defaultModel, getById }
})
