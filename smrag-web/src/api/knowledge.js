import request from '@/utils/request'

// 上传知识文档
export function uploadDocument(
  file,
  knowledgeBaseCode,
  roles
) {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('knowledgeBaseCode', knowledgeBaseCode)
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
export function getDocumentList(knowledgeBaseCode) {
  return request({
    url: '/knowledge/list',
    method: 'get',
    params: { knowledgeBaseCode }
  })
}

// 删除文档
export function deleteDocument(id, knowledgeBaseCode) {
  return request({
    url: `/knowledge/${id}`,
    method: 'delete',
    params: { knowledgeBaseCode }
  })
}

export function setDocumentPermissions(id, roleCodes) {
  return request.post(`/documents/${id}/permissions`, {
    permissions: roleCodes.map(roleCode => ({ roleCode, canRead: true }))
  })
}

export function getDocumentFile(id) {
  return request({
    url: `/knowledge/${id}/file`,
    method: 'get',
    responseType: 'blob',
    timeout: 120000
  })
}
