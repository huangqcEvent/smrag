import request from '@/utils/request'

// 获取产品详情
export function getProduct(id) {
  return request({
    url: `/product/${id}`,
    method: 'get'
  })
}

// 搜索产品
export function searchProducts(productName) {
  return request({
    url: '/product/search',
    method: 'get',
    params: { productName }
  })
}

// 获取产品列表
export function getProductList() {
  return request({
    url: '/product/list',
    method: 'get'
  })
}