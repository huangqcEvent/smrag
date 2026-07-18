import { createSseParser } from '../utils/sse.js'

export class ChatStreamError extends Error {
  constructor(message, code) {
    super(message)
    this.name = 'ChatStreamError'
    this.code = code
  }
}

const createHttpError = async (response) => {
  let payload
  try {
    payload = JSON.parse(await response.text())
  } catch {
    payload = null
  }

  const message = payload?.message || payload?.error || `请求失败 (${response.status})`
  return new ChatStreamError(message, payload?.code || response.status)
}

export async function postChatStream(data, options = {}) {
  const {
    signal,
    onEvent = () => {},
    fetchImpl = globalThis.fetch
  } = options

  // 从 localStorage 读取 token（确保每次请求都获取最新值）
  const token = globalThis.localStorage?.getItem('accessToken')
  const headers = {
    Accept: 'text/event-stream',
    'Content-Type': 'application/json'
  }
  if (token) {
    headers.Authorization = `Bearer ${token}`
  }

  const response = await fetchImpl('/api/chat/stream', {
    method: 'POST',
    headers,
    body: JSON.stringify(data),
    signal
  })

  if (!response.ok) throw await createHttpError(response)
  const contentType = response.headers.get('content-type') || ''
  if (!contentType.toLowerCase().includes('text/event-stream')) {
    await response.body?.cancel().catch(() => {})
    throw new ChatStreamError('服务器未返回流式响应', response.status)
  }
  if (!response.body) throw new ChatStreamError('浏览器未收到流式响应', response.status)

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let serverError = null
  let completed = false
  const parser = createSseParser(({ event, data: eventData }) => {
    if (completed || serverError) return
    let payload
    try {
      payload = JSON.parse(eventData)
    } catch {
      throw new ChatStreamError('流式响应格式错误')
    }

    const streamEvent = { ...payload, type: event }
    onEvent(streamEvent)
    if (event === 'done') completed = true
    if (event === 'error') {
      serverError = new ChatStreamError(payload.message || '生成回答失败', payload.code)
    }
  })

  try {
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      parser.feed(decoder.decode(value, { stream: true }))
      if (completed || serverError) {
        await reader.cancel().catch(() => {})
        break
      }
    }
    if (!completed && !serverError) {
      parser.feed(decoder.decode())
      parser.flush()
    }
  } catch (error) {
    await reader.cancel().catch(() => {})
    throw error
  } finally {
    reader.releaseLock()
  }

  if (serverError) throw serverError
  if (!completed) throw new ChatStreamError('流式响应意外中断')
}
