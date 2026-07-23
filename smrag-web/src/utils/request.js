import axios from 'axios'
import { notifyError } from './errorFeedback'
import { handleSessionExpiry } from './sessionExpiry'

// 创建axios实例
const request = axios.create({
  baseURL: '/api',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json'
  }
})

const authorizationTokenOf = config => {
  const authorization = config?.headers?.Authorization
    || config?.headers?.get?.('Authorization')
  return typeof authorization === 'string'
    ? authorization.replace(/^Bearer\s+/i, '')
    : ''
}

const handleAuthenticationError = error => {
  if (Number(error?.response?.status) !== 401) return false

  if (error.config?.skipAuthRedirect) {
    if (!error.config?.suppressError) {
      notifyError(error, error.config?.errorMessage)
    }
    return true
  }

  const requestToken = authorizationTokenOf(error.config)
  const currentToken = globalThis.localStorage?.getItem('accessToken')
  if (handleSessionExpiry(error, { requestToken, currentToken })) {
    notifyError(error, '登录已过期，请重新登录')
  }
  return true
}

// 请求拦截器
request.interceptors.request.use(
  config => {
    // 直接从 localStorage 读取 token（确保每次请求都获取最新值）
    const token = globalThis.localStorage?.getItem('accessToken')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  error => {
    return Promise.reject(error)
  }
)

// 响应拦截器
request.interceptors.response.use(
  response => {
    const body = response.data
    if (body && typeof body === 'object' && 'code' in body && 'data' in body) {
      if (body.code === 200) {
        return body.data
      }
      const error = new Error(body.message || '')
      error.name = 'RequestError'
      error.response = {
        status: Number(body.code) || response.status,
        data: body
      }
      error.config = response.config
      if (
        !handleAuthenticationError(error)
        && !response.config?.suppressError
      ) {
        notifyError(error, response.config?.errorMessage)
      }
      return Promise.reject(error)
    }
    return body
  },
  error => {
    if (
      !handleAuthenticationError(error)
      && !error.config?.suppressError
    ) {
      notifyError(error, error.config?.errorMessage)
    }
    return Promise.reject(error)
  }
)

export default request
