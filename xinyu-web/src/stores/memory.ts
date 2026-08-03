import { ref } from 'vue'
import { defineStore } from 'pinia'
import { memoriesApi } from '@/api/modules/memory'
import type { MemoryVO } from '@/types/api'

/**
 * 记忆 store
 *
 * 懒加载: 进入记忆管理页才加载。
 * 按 characterId 筛选在前端做（列表全量拉取后本地过滤）, 避免切角色反复请求。
 */
export const useMemoriesStore = defineStore('memories', () => {
  const list = ref<MemoryVO[]>([])
  const loaded = ref(false)
  const loading = ref(false)

  async function load(force = false): Promise<void> {
    if (loaded.value && !force) return
    loading.value = true
    try {
      list.value = await memoriesApi.list()
      loaded.value = true
    } finally {
      loading.value = false
    }
  }

  /** 按角色筛选（本地过滤） */
  function byCharacter(characterId: string | null): MemoryVO[] {
    if (!characterId) return list.value
    return list.value.filter((m) => m.characterId === characterId)
  }

  /** 涉及到的角色列表（去重, 用于筛选胶囊） */
  function characters(): { id: string; name: string }[] {
    const seen = new Map<string, string>()
    for (const m of list.value) {
      if (!seen.has(m.characterId)) {
        seen.set(m.characterId, m.characterName)
      }
    }
    return Array.from(seen, ([id, name]) => ({ id, name }))
  }

  return { list, loaded, loading, load, byCharacter, characters }
})
