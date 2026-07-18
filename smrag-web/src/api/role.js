import request from '@/utils/request'

export const getRoles = () => request.get('/roles')
export const createRole = data => request.post('/roles', data)
export const updateRole = (id, data) => request.put(`/roles/${id}`, data)
export const deleteRole = id => request.delete(`/roles/${id}`)
