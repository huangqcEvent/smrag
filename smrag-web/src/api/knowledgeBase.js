import request from '@/utils/request'

export const getAccessibleKnowledgeBases = () => request.get('/knowledge-bases')
export const getAdminKnowledgeBases = () => request.get('/admin/knowledge-bases')
export const createKnowledgeBase = data => request.post('/admin/knowledge-bases', data)
export const updateKnowledgeBase = (id, data) =>
  request.put(`/admin/knowledge-bases/${id}`, data)
export const replaceKnowledgeBaseRoles = (id, data) =>
  request.put(`/admin/knowledge-bases/${id}/roles`, data)
export const changeKnowledgeBaseStatus = (id, data) =>
  request.patch(`/admin/knowledge-bases/${id}/status`, data)
export const deleteKnowledgeBase = id =>
  request.delete(`/admin/knowledge-bases/${id}`)
export const moveDocument = (id, targetKnowledgeBaseCode) =>
  request.post(`/admin/documents/${id}/move`, { targetKnowledgeBaseCode })
