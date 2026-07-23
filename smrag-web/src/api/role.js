import request from '@/utils/request'

export const getRoles = () => request({
  url: '/roles',
  method: 'get',
  errorMessage: '加载角色列表失败'
})
export const createRole = data => request({
  url: '/roles',
  method: 'post',
  data,
  errorMessage: '新增角色失败'
})
export const updateRole = (id, data) => request({
  url: `/roles/${id}`,
  method: 'put',
  data,
  errorMessage: '更新角色失败'
})
export const deleteRole = id => request({
  url: `/roles/${id}`,
  method: 'delete',
  errorMessage: '删除角色失败'
})
