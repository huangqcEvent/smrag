import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { login as loginApi, refreshToken as refreshTokenApi, getCurrentUser, logout as logoutApi } from '@/api/auth'
import router from '@/router'
import { useChatStore } from '@/stores/chat'

export const useAuthStore = defineStore('auth', () => {
  // 状态
  const accessToken = ref(localStorage.getItem('accessToken') || '')
  const refreshTokenValue = ref(localStorage.getItem('refreshToken') || '')
  const user = ref(null)

  // 计算属性
  const isLoggedIn = computed(() => !!accessToken.value)
  const userRole = computed(() => user.value?.roles?.[0] || 'USER')
  const isAdmin = computed(() => userRole.value === 'ADMIN')
  const username = computed(() => user.value?.username || '')

  /**
   * 登录
   */
  async function login(username, password) {
    try {
      const response = await loginApi({ username, password })
      useChatStore().resetForAccountChange()
      setTokens(response.accessToken, response.refreshToken)
      user.value = response.user
      return response
    } catch (error) {
      throw error
    }
  }

  /**
   * 登出
   */
  function logout() {
    // 调用后端登出接口
    if (accessToken.value) {
      logoutApi().catch(() => {})
    }
    // 清除状态
    useChatStore().resetForAccountChange()
    clearTokens()
    user.value = null
  }

  /**
   * 刷新令牌
   */
  async function refreshAccessToken() {
    if (!refreshTokenValue.value) {
      throw new Error('No refresh token')
    }
    try {
      const response = await refreshTokenApi(refreshTokenValue.value)
      setTokens(response.accessToken, response.refreshToken)
      user.value = response.user
      return response
    } catch (error) {
      // 刷新失败，清除状态并跳转登录
      logout()
      router.push('/login')
      throw error
    }
  }

  /**
   * 获取当前用户信息
   */
  async function fetchCurrentUser() {
    if (!accessToken.value) return
    try {
      const userData = await getCurrentUser()
      user.value = userData
    } catch (error) {
      console.error('Failed to fetch current user:', error)
    }
  }

  /**
   * 设置令牌
   */
  function setTokens(access, refresh) {
    accessToken.value = access
    refreshTokenValue.value = refresh
    localStorage.setItem('accessToken', access)
    localStorage.setItem('refreshToken', refresh)
  }

  /**
   * 清除令牌
   */
  function clearTokens() {
    accessToken.value = ''
    refreshTokenValue.value = ''
    localStorage.removeItem('accessToken')
    localStorage.removeItem('refreshToken')
  }

  return {
    // 状态
    accessToken,
    refreshTokenValue,
    user,
    // 计算属性
    isLoggedIn,
    userRole,
    isAdmin,
    username,
    // 方法
    login,
    logout,
    refreshAccessToken,
    fetchCurrentUser
  }
})
