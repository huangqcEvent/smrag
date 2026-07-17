import { defineStore } from 'pinia'
import { ref } from 'vue'
import { chatStream, getChatHistory } from '@/api/chat'
import { applyChatStreamEvent, createPendingChatMessage } from './chatStreamState'

export const useChatStore = defineStore('chat', () => {
  const currentSessionId = ref('')
  const chatHistory = ref([])
  const loading = ref(false)
  let activeController = null
  let activeMessage = null

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
  const askQuestion = async (question, options = {}) => {
    if (loading.value) return null

    initSession()
    const controller = new AbortController()
    chatHistory.value.push(createPendingChatMessage(question, currentSessionId.value))
    const message = chatHistory.value.at(-1)
    activeController = controller
    activeMessage = message
    loading.value = true

    try {
      await chatStream({
        sessionId: currentSessionId.value,
        question,
        userId: options.userId ?? null,
        knowledgeBaseId: options.knowledgeBaseId || 'default',
        documentId: options.documentId ?? null
      }, {
        signal: controller.signal,
        onEvent: event => {
          applyChatStreamEvent(message, event)
          if (event.sessionId) currentSessionId.value = event.sessionId
        }
      })

      return message
    } catch (error) {
      if (error?.name === 'AbortError') {
        if (!message.answer) {
          removeMessage(message)
        } else if (message.state !== 'stopped') {
          applyChatStreamEvent(message, { type: 'stopped' })
        }
        return message
      }
      if (!message.answer) {
        removeMessage(message)
      } else if (message.state !== 'error') {
        applyChatStreamEvent(message, { type: 'error', message: error?.message })
      }
      throw error
    } finally {
      if (activeController === controller) {
        activeController = null
        activeMessage = null
        loading.value = false
      }
    }
  }

  const removeMessage = (message) => {
    const index = chatHistory.value.indexOf(message)
    if (index >= 0) chatHistory.value.splice(index, 1)
  }

  // 停止当前生成
  const stopGeneration = () => {
    if (!activeController || !activeMessage) return false
    if (!['pending', 'streaming'].includes(activeMessage.state)) return false
    if (activeMessage) applyChatStreamEvent(activeMessage, { type: 'stopped' })
    activeController.abort()
    return true
  }

  // 加载历史记录
  const loadHistory = async (sessionId) => {
    stopGeneration()
    try {
      const history = await getChatHistory(sessionId)
      chatHistory.value = history.map(item => {
        let citations = []
        try {
          citations = JSON.parse(item.referenceDocs || '[]')
        } catch {
          citations = []
        }
        return {
          ...item,
          citations,
          references: [...new Set(citations.map(citation => citation.documentName).filter(Boolean))],
          phase: 'DONE',
          state: 'done',
          error: ''
        }
      })
      currentSessionId.value = sessionId
    } catch (error) {
      console.error('加载历史记录失败:', error)
    }
  }

  // 清空会话
  const clearSession = () => {
    stopGeneration()
    currentSessionId.value = generateSessionId()
    chatHistory.value = []
  }

  return {
    currentSessionId,
    chatHistory,
    loading,
    askQuestion,
    stopGeneration,
    loadHistory,
    clearSession,
    initSession
  }
})
