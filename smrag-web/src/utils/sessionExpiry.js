const statusOf = error => {
  const responseStatus = Number(error?.response?.status)
  if (Number.isInteger(responseStatus)) return responseStatus
  const errorCode = Number(error?.code)
  return Number.isInteger(errorCode) ? errorCode : null
}

let expireSessionHandler = () => {}

export function configureSessionExpiry(handler) {
  expireSessionHandler = typeof handler === 'function' ? handler : () => {}
}

export function handleSessionExpiry(error, {
  requestToken = error?.requestToken,
  currentToken = globalThis.localStorage?.getItem('accessToken'),
  expireSession = expireSessionHandler
} = {}) {
  if (statusOf(error) !== 401) return false
  if (!requestToken || requestToken !== currentToken) {
    if (error && typeof error === 'object') {
      error.suppressUserFeedback = true
    }
    return false
  }
  expireSession()
  return true
}
