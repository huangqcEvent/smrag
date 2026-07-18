import request from '@/utils/request'

/**
 * 获取所有用户列表
 * @returns {Promise<Array>} 用户列表
 */
export function getUserList() {
  return request.get('/users')
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
  return request.post('/users', data)
}

/**
 * 修改用户角色
 * @param {number} userId - 用户ID
 * @param {string} roleCode - 角色编码
 * @returns {Promise<void>}
 */
export function updateUserRole(userId, roleCode) {
  return request.put(`/users/${userId}/roles`, { roleCode })
}

/**
 * 重置用户密码
 * @param {number} userId - 用户ID
 * @param {string} newPassword - 新密码
 * @returns {Promise<void>}
 */
export function resetPassword(userId, newPassword) {
  return request.put(`/users/${userId}/password`, null, {
    params: { newPassword }
  })
}

/**
 * 禁用用户
 * @param {number} userId - 用户ID
 * @returns {Promise<void>}
 */
export function disableUser(userId) {
  return request.put(`/users/${userId}/disable`)
}

/**
 * 启用用户
 * @param {number} userId - 用户ID
 * @returns {Promise<void>}
 */
export function enableUser(userId) {
  return request.put(`/users/${userId}/enable`)
}