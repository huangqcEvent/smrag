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
    },
    errorMessage: '上传文档失败'
  })
}

// 获取文档列表
export function getDocumentList({
  knowledgeBaseCode,
  pageNum,
  pageSize,
  keyword,
  status
}) {
  return request({
    url: '/knowledge/list',
    method: 'get',
    params: {
      knowledgeBaseCode,
      pageNum,
      pageSize,
      ...(keyword ? { keyword } : {}),
      ...(status ? { status } : {})
    },
    errorMessage: '加载文档列表失败'
  })
}

// 删除文档
export function deleteDocument(id, knowledgeBaseCode) {
  return request({
    url: `/knowledge/${id}`,
    method: 'delete',
    params: { knowledgeBaseCode },
    errorMessage: '删除文档失败'
  })
}

export function setDocumentPermissions(id, roleCodes) {
  return request({
    url: `/documents/${id}/permissions`,
    method: 'post',
    data: {
      permissions: roleCodes.map(roleCode => ({ roleCode, canRead: true }))
    },
    errorMessage: '更新文档权限失败'
  })
}

export function getDocumentFile(id) {
  return request({
    url: `/knowledge/${id}/file`,
    method: 'get',
    responseType: 'blob',
    timeout: 120000,
    errorMessage: '读取文档失败'
  })
}
