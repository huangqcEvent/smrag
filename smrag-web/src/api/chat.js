import request from '@/utils/request'

// 聊天接口
export function chatAsk(data) {
  return request({
    url: '/chat/ask',
    method: 'post',
    data
  })
}

// 获取聊天历史
export function getChatHistory(sessionId) {
  return request({
    url: `/chat/history/${sessionId}`,
    method: 'get'
  })
}