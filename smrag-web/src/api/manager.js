import request from '@/utils/request'

// 获取管理人详情
export function getManager(id) {
  return request({
    url: `/manager/${id}`,
    method: 'get'
  })
}

// 搜索管理人
export function searchManagers(managerName) {
  return request({
    url: '/manager/search',
    method: 'get',
    params: { managerName }
  })
}

// 获取管理人列表
export function getManagerList() {
  return request({
    url: '/manager/list',
    method: 'get'
  })
}