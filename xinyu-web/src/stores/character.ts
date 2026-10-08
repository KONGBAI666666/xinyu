import { ref } from 'vue'
import { defineStore } from 'pinia'
import { charactersApi } from '@/api/modules/character'
import type { CharacterVO } from '@/types/api'

/**
 * 角色 store
 *
 * 懒加载: 进入角色管理页或新建会话选择角色时才加载。
 * 官方角色排前, 自建按创建时间倒序（后端已排序）。
 */
export const useCharactersStore = defineStore('characters', () => {
  const list = ref<CharacterVO[]>([])
  const loaded = ref(false)
  const loading = ref(false)

  async function load(force = false): Promise<void> {
    if (loaded.value && !force) return
    loading.value = true
    try {
      list.value = await charactersApi.list()
      loaded.value = true
    } finally {
      loading.value = false
    }
  }

  /** 单项替换 (M5 头像上传后局部刷新, 免整表重拉) */
  function replaceOne(updated: CharacterVO): void {
    const idx = list.value.findIndex((c) => c.id === updated.id)
    if (idx !== -1) list.value[idx] = updated
  }

  /** 官方角色 */
  function official(): CharacterVO[] {
    return list.value.filter((c) => c.creatorType === 'OFFICIAL')
  }

  /** 我创建的角色 */
  function mine(): CharacterVO[] {
    return list.value.filter((c) => c.mine)
  }

  /** 按 id 查角色 */
  function getById(id: string | null): CharacterVO | null {
    if (!id) return null
    return list.value.find((c) => c.id === id) ?? null
  }

  return { list, loaded, loading, load, official, mine, getById, replaceOne }
})
