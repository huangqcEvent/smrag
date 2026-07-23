import request from '@/utils/request'
import { notifyError } from '@/utils/errorFeedback'
import { postChatStream } from './chatStream'

// 聊天接口
export function chatAsk(data) {
  return request({
    url: '/chat/ask',
    method: 'post',
    data,
    errorMessage: '生成回答失败'
  })
}

// 流式聊天接口
export async function chatStream(data, options = {}) {
  try {
    return await postChatStream(data, options)
  } catch (error) {
    notifyError(error, '生成回答失败')
    throw error
  }
}

// 获取聊天历史
export function getChatHistory(sessionId) {
  return request({
    url: `/chat/history/${sessionId}`,
    method: 'get',
    errorMessage: '加载会话记录失败'
  })
}

// 获取用户会话列表
export function getUserSessions() {
  return request({
    url: '/chat/sessions',
    method: 'get',
    errorMessage: '加载会话列表失败'
  })
}
