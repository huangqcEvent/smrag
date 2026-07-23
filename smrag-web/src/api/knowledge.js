import request from '@/utils/request'
import { MAIN_KNOWLEDGE_BASE } from '@/constants/knowledgeBase'

// 上传知识文档
export function uploadDocument(
  file,
  knowledgeBaseId = MAIN_KNOWLEDGE_BASE.code,
  roles = ['ADMIN', 'USER']
) {
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
export function getDocumentList(knowledgeBaseId = MAIN_KNOWLEDGE_BASE.code) {
  return request({
    url: '/knowledge/list',
    method: 'get',
    params: { knowledgeBaseId }
  })
}

// 删除文档
export function deleteDocument(id, knowledgeBaseId = MAIN_KNOWLEDGE_BASE.code) {
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

export function getDocumentFile(id) {
  return request({
    url: `/knowledge/${id}/file`,
    method: 'get',
    responseType: 'blob'
  })
}

export function getKnowledgeBases() {
  return request({
    url: '/knowledge-bases',
    method: 'get'
  })
}
