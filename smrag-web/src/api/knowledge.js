import request from '@/utils/request'

// 上传知识文档
export function uploadDocument(file, knowledgeBaseId = 'default', roles = ['ADMIN', 'USER']) {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('knowledgeBaseId', knowledgeBaseId)
  formData.append('roles', roles.join(','))
  return request({
    url: '/knowledge/upload',
    method: 'post',
    data: formData,
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  })
}

// 获取文档列表
export function getDocumentList(knowledgeBaseId = 'default') {
  return request({
    url: '/knowledge/list',
    method: 'get',
    params: { knowledgeBaseId }
  })
}

// 删除文档
export function deleteDocument(id, knowledgeBaseId = 'default') {
  return request({
    url: `/knowledge/${id}`,
    method: 'delete',
    params: { knowledgeBaseId }
  })
}

export function setDocumentPermissions(id, roleCodes) {
  return request.post(`/documents/${id}/permissions`, {
    permissions: roleCodes.map(roleCode => ({ roleCode, canRead: true }))
  })
}
