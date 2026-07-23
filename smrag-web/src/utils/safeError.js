const DEFAULT_ERROR_MESSAGE = '当前操作未完成'

const TECHNICAL_MESSAGE_PATTERNS = [
  /系统繁忙.*稍后重试/i,
  /request failed with status code/i,
  /^network error$/i,
  /timeout of \d+ms exceeded/i,
  /(?:java|javax|org|com)\.[\w.$]*(?:Exception|Error)/i,
  /\b(?:SQL|JDBC|MyBatis|Hibernate|Axios)\b/i,
  /\b(?:NullPointerException|SQLException|stack trace)\b/i,
  /<html[\s>]/i
]

const text = value => typeof value === 'string' ? value.trim() : ''

const safeFallback = fallback => {
  const value = text(fallback)
  return value && !isTechnicalErrorMessage(value)
    ? value
    : DEFAULT_ERROR_MESSAGE
}

const statusOf = error => {
  const responseStatus = Number(error?.response?.status)
  if (Number.isInteger(responseStatus) && responseStatus > 0) {
    return responseStatus
  }
  const errorCode = Number(error?.code)
  return Number.isInteger(errorCode) && errorCode > 0 ? errorCode : null
}

const serverMessageOf = error => {
  const data = error?.response?.data
  if (data && typeof data === 'object') {
    return text(data.message) || text(data.error)
  }
  return text(error?.message)
}

const isTimeout = error =>
  error?.code === 'ECONNABORTED'
  || error?.code === 'ETIMEDOUT'
  || /\btimeout\b/i.test(text(error?.message))

const isNetworkFailure = error =>
  !error?.response && (
    error?.code === 'ERR_NETWORK'
    || /^network error$/i.test(text(error?.message))
    || /failed to fetch|fetch failed|load failed/i.test(text(error?.message))
  )

export function isTechnicalErrorMessage(message) {
  const value = text(message)
  if (!value) return false
  return TECHNICAL_MESSAGE_PATTERNS.some(pattern => pattern.test(value))
}

export function isSilentError(error) {
  if (error === 'cancel' || error === 'close') return true
  return error?.name === 'AbortError'
    || error?.name === 'CanceledError'
    || error?.code === 'ERR_CANCELED'
}

export function toUserErrorMessage(
  error,
  fallback = DEFAULT_ERROR_MESSAGE
) {
  const operationFallback = safeFallback(fallback)
  if (isSilentError(error)) return ''

  const status = statusOf(error)
  if (status === 401) return '登录已过期，请重新登录'
  if (isTimeout(error)) return '请求超时，请检查网络连接'
  if (isNetworkFailure(error)) return '网络连接异常，请检查网络'

  if (status != null && status >= 500) return operationFallback

  const serverMessage = serverMessageOf(error)
  if (serverMessage && !isTechnicalErrorMessage(serverMessage)) {
    return serverMessage
  }

  if (status === 403) return '没有权限执行该操作'
  if (status === 404) return '请求的内容不存在或已被删除'
  if (status === 409) return '数据已发生变化，请刷新页面'
  return operationFallback
}
