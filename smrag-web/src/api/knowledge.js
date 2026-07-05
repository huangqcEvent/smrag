import request from '@/utils/request'

// 上传知识文档
export function uploadDocument(file) {
  const formData = new FormData()
  formData.append('file', file)
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
export function getDocumentList() {
  return request({
    url: '/knowledge/list',
    method: 'get'
  })
}

// 删除文档
export function deleteDocument(id) {
  return request({
    url: `/knowledge/${id}`,
    method: 'delete'
  })
}