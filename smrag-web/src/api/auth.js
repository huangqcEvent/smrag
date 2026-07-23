import request from '@/utils/request'

/**
 * 登录请求
 * @param {Object} data - 登录数据
 * @param {string} data.username - 用户名
 * @param {string} data.password - 密码
 * @returns {Promise<Object>} 登录响应
 */
export function login(data) {
  return request({
    url: '/auth/login',
    method: 'post',
    data,
    skipAuthRedirect: true,
    errorMessage: '登录失败'
  })
}

/**
 * 刷新令牌
 * @param {string} refreshToken - 刷新令牌
 * @returns {Promise<Object>} 登录响应
 */
export function refreshToken(refreshToken) {
  return request({
    url: '/auth/refresh',
    method: 'post',
    data: { refreshToken },
    errorMessage: '刷新登录状态失败'
  })
}

/**
 * 获取当前用户信息
 * @returns {Promise<Object>} 用户信息
 */
export function getCurrentUser() {
  return request({
    url: '/auth/me',
    method: 'get',
    errorMessage: '加载用户信息失败'
  })
}

/**
 * 用户登出
 * @returns {Promise<void>}
 */
export function logout() {
  return request({
    url: '/auth/logout',
    method: 'post',
    suppressError: true
  })
}
