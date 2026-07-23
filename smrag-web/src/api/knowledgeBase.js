import request from '@/utils/request'

export const getAccessibleKnowledgeBases = () => request({
  url: '/knowledge-bases',
  method: 'get',
  errorMessage: '加载知识库失败'
})
export const getAdminKnowledgeBases = () => request({
  url: '/admin/knowledge-bases',
  method: 'get',
  errorMessage: '加载知识库管理列表失败'
})
export const createKnowledgeBase = data => request({
  url: '/admin/knowledge-bases',
  method: 'post',
  data,
  errorMessage: '创建知识库失败'
})
export const updateKnowledgeBase = (id, data) =>
  request({
    url: `/admin/knowledge-bases/${id}`,
    method: 'put',
    data,
    errorMessage: '更新知识库失败'
  })
export const replaceKnowledgeBaseRoles = (id, data) =>
  request({
    url: `/admin/knowledge-bases/${id}/roles`,
    method: 'put',
    data,
    errorMessage: '更新知识库权限失败'
  })
export const changeKnowledgeBaseStatus = (id, data) =>
  request({
    url: `/admin/knowledge-bases/${id}/status`,
    method: 'patch',
    data,
    errorMessage: '更新知识库状态失败'
  })
export const deleteKnowledgeBase = id =>
  request({
    url: `/admin/knowledge-bases/${id}`,
    method: 'delete',
    errorMessage: '删除知识库失败'
  })
export const moveDocument = (id, targetKnowledgeBaseCode) =>
  request({
    url: `/admin/documents/${id}/move`,
    method: 'post',
    data: { targetKnowledgeBaseCode },
    errorMessage: '移动文档失败'
  })
