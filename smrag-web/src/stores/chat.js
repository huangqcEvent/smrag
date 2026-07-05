import { defineStore } from 'pinia'
import { ref } from 'vue'
import { chatAsk, getChatHistory } from '@/api/chat'

export const useChatStore = defineStore('chat', () => {
  const currentSessionId = ref('')
  const chatHistory = ref([])
  const loading = ref(false)

  // 生成会话ID
  const generateSessionId = () => {
    return 'session_' + Date.now() + '_' + Math.random().toString(36).substring(2, 15)
  }

  // 初始化会话
  const initSession = () => {
    if (!currentSessionId.value) {
      currentSessionId.value = generateSessionId()
    }
  }

  // 发送问题
  const askQuestion = async (question, userId = null) => {
    loading.value = true
    try {
      initSession()
      const response = await chatAsk({
        sessionId: currentSessionId.value,
        question,
        userId
      })

      chatHistory.value.push({
        question,
        answer: response.answer,
        routeType: response.routeType,
        references: response.references,
        sessionId: response.sessionId
      })

      return response
    } finally {
      loading.value = false
    }
  }

  // 加载历史记录
  const loadHistory = async (sessionId) => {
    try {
      const history = await getChatHistory(sessionId)
      chatHistory.value = history
      currentSessionId.value = sessionId
    } catch (error) {
      console.error('加载历史记录失败:', error)
    }
  }

  // 清空会话
  const clearSession = () => {
    currentSessionId.value = generateSessionId()
    chatHistory.value = []
  }

  return {
    currentSessionId,
    chatHistory,
    loading,
    askQuestion,
    loadHistory,
    clearSession,
    initSession
  }
})