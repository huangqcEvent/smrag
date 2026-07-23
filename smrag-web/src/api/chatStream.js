import { createSseParser } from '../utils/sse.js'

export class ChatStreamError extends Error {
  constructor(message, code, { errorCode, requestToken } = {}) {
    super(message)
    this.name = 'ChatStreamError'
    this.code = code
    this.errorCode = errorCode
    this.requestToken = requestToken
  }
}

const createHttpError = async (response, requestToken) => {
  let payload
  try {
    payload = JSON.parse(await response.text())
  } catch {
    payload = null
  }

  const message = payload?.message || payload?.error || `请求失败 (${response.status})`
  const payloadCode = Number(payload?.code)
  const code = Number.isInteger(payloadCode) ? payloadCode : response.status
  return new ChatStreamError(message, code, {
    errorCode: payload?.errorCode,
    requestToken
  })
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

  if (!response.ok) throw await createHttpError(response, token)
  const contentType = response.headers.get('content-type') || ''
  if (!contentType.toLowerCase().includes('text/event-stream')) {
    await response.body?.cancel().catch(() => {})
    throw new ChatStreamError(
      '服务器未返回流式响应',
      response.status,
      { requestToken: token }
    )
  }
  if (!response.body) {
    throw new ChatStreamError(
      '浏览器未收到流式响应',
      response.status,
      { requestToken: token }
    )
  }

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
      throw new ChatStreamError(
        '流式响应格式错误',
        undefined,
        { requestToken: token }
      )
    }

    const streamEvent = { ...payload, type: event }
    onEvent(streamEvent)
    if (event === 'done') completed = true
    if (event === 'error') {
      serverError = new ChatStreamError(
        payload.message || '生成回答失败',
        payload.code,
        {
          errorCode: payload.errorCode,
          requestToken: token
        }
      )
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
  if (!completed) {
    throw new ChatStreamError(
      '流式响应意外中断',
      undefined,
      { requestToken: token }
    )
  }
}
