import { defineStore } from 'pinia'
import { ref } from 'vue'
import { uploadDocument, getDocumentList, deleteDocument } from '@/api/knowledge'

export const useKnowledgeStore = defineStore('knowledge', () => {
  const documents = ref([])
  const loading = ref(false)

  // 获取文档列表
  const fetchDocuments = async () => {
    loading.value = true
    try {
      documents.value = await getDocumentList()
    } finally {
      loading.value = false
    }
  }

  // 上传文档
  const upload = async (file) => {
    try {
      await uploadDocument(file)
      await fetchDocuments()
      return true
    } catch (error) {
      console.error('上传失败:', error)
      return false
    }
  }

  // 删除文档
  const remove = async (id) => {
    try {
      await deleteDocument(id)
      await fetchDocuments()
      return true
    } catch (error) {
      console.error('删除失败:', error)
      return false
    }
  }

  return {
    documents,
    loading,
    fetchDocuments,
    upload,
    remove
  }
})