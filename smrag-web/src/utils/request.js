import axios from 'axios'
import router from '@/router'
import { useAuthStore } from '@/stores/auth'
import { notifyError } from './errorFeedback'

// 创建axios实例
const request = axios.create({
  baseURL: '/api',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json'
  }
})

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
      notifyError(error)
      return Promise.reject(error)
    }
    return body
  },
  error => {
    // 401 未登录或 Token 过期
    if (error.response?.status === 401) {
      const failedAuthorization = error.config?.headers?.Authorization
      const currentToken = globalThis.localStorage?.getItem('accessToken')
      if (failedAuthorization === `Bearer ${currentToken}`) {
        const authStore = useAuthStore()
        authStore.logout()
        router.push('/login')
        notifyError(error, '登录已过期，请重新登录')
      }
    } else {
      notifyError(error)
    }
    return Promise.reject(error)
  }
)

export default request
