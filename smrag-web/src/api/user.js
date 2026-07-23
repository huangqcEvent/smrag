import request from '@/utils/request'

/**
 * 获取所有用户列表
 * @returns {Promise<Array>} 用户列表
 */
export function getUserList() {
  return request({
    url: '/users',
    method: 'get',
    errorMessage: '加载用户列表失败'
  })
}

/**
 * 添加用户
 * @param {Object} data - 用户数据
 * @param {string} data.username - 用户名
 * @param {string} data.password - 密码
 * @param {string} data.nickname - 昵称
 * @param {string} data.roleCode - 角色编码
 * @returns {Promise<number>} 用户ID
 */
export function addUser(data) {
  return request({
    url: '/users',
    method: 'post',
    data,
    errorMessage: '添加用户失败'
  })
}

/**
 * 修改用户角色
 * @param {number} userId - 用户ID
 * @param {string} roleCode - 角色编码
 * @returns {Promise<void>}
 */
export function updateUserRole(userId, roleCode) {
  return request({
    url: `/users/${userId}/roles`,
    method: 'put',
    data: { roleCode },
    errorMessage: '修改角色失败'
  })
}

/**
 * 重置用户密码
 * @param {number} userId - 用户ID
 * @param {string} newPassword - 新密码
 * @returns {Promise<void>}
 */
export function resetPassword(userId, newPassword) {
  return request({
    url: `/users/${userId}/password`,
    method: 'put',
    data: null,
    params: { newPassword },
    errorMessage: '重置密码失败'
  })
}

/**
 * 禁用用户
 * @param {number} userId - 用户ID
 * @returns {Promise<void>}
 */
export function disableUser(userId) {
  return request({
    url: `/users/${userId}/disable`,
    method: 'put',
    errorMessage: '禁用用户失败'
  })
}

/**
 * 启用用户
 * @param {number} userId - 用户ID
 * @returns {Promise<void>}
 */
export function enableUser(userId) {
  return request({
    url: `/users/${userId}/enable`,
    method: 'put',
    errorMessage: '启用用户失败'
  })
}
