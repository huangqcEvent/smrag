import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getManagerList, searchManagers, getManager } from '@/api/manager'

export const useManagerStore = defineStore('manager', () => {
  const managers = ref([])
  const currentManager = ref(null)
  const loading = ref(false)

  // 获取管理人列表
  const fetchManagers = async () => {
    loading.value = true
    try {
      managers.value = await getManagerList()
    } finally {
      loading.value = false
    }
  }

  // 搜索管理人
  const search = async (managerName) => {
    loading.value = true
    try {
      if (managerName) {
        managers.value = await searchManagers(managerName)
      } else {
        await fetchManagers()
      }
    } finally {
      loading.value = false
    }
  }

  // 获取管理人详情
  const fetchManager = async (id) => {
    loading.value = true
    try {
      currentManager.value = await getManager(id)
    } finally {
      loading.value = false
    }
  }

  return {
    managers,
    currentManager,
    loading,
    fetchManagers,
    search,
    fetchManager
  }
})