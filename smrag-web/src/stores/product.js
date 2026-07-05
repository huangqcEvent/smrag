import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getProductList, searchProducts, getProduct } from '@/api/product'

export const useProductStore = defineStore('product', () => {
  const products = ref([])
  const currentProduct = ref(null)
  const loading = ref(false)

  // 获取产品列表
  const fetchProducts = async () => {
    loading.value = true
    try {
      products.value = await getProductList()
    } finally {
      loading.value = false
    }
  }

  // 搜索产品
  const search = async (productName) => {
    loading.value = true
    try {
      if (productName) {
        products.value = await searchProducts(productName)
      } else {
        await fetchProducts()
      }
    } finally {
      loading.value = false
    }
  }

  // 获取产品详情
  const fetchProduct = async (id) => {
    loading.value = true
    try {
      currentProduct.value = await getProduct(id)
    } finally {
      loading.value = false
    }
  }

  return {
    products,
    currentProduct,
    loading,
    fetchProducts,
    search,
    fetchProduct
  }
})