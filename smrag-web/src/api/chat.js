import { ElMessage } from 'element-plus'
import request from '@/utils/request'
import { postChatStream } from './chatStream'

// 聊天接口
export function chatAsk(data) {
  return request({
    url: '/chat/ask',
    method: 'post',
    data
  })
}

// 流式聊天接口
export async function chatStream(data, options = {}) {
  try {
    return await postChatStream(data, options)
  } catch (error) {
    if (error?.name !== 'AbortError') {
      ElMessage.error(error?.message || '请求失败')
    }
    throw error
  }
}

// 获取聊天历史
export function getChatHistory(sessionId) {
  return request({
    url: `/chat/history/${sessionId}`,
    method: 'get'
  })
}

// 获取用户会话列表
export function getUserSessions() {
  return request({
    url: '/chat/sessions',
    method: 'get'
  })
}
