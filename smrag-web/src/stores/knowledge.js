import { defineStore } from 'pinia'
import { ref } from 'vue'
import { uploadDocument, getDocumentList, deleteDocument, setDocumentPermissions } from '@/api/knowledge'

export const useKnowledgeStore = defineStore('knowledge', () => {
  const documents = ref([])
  const loading = ref(false)
  const knowledgeBaseId = ref('default')

  // 获取文档列表
  const fetchDocuments = async () => {
    loading.value = true
    try {
      documents.value = await getDocumentList(knowledgeBaseId.value.trim() || 'default')
    } finally {
      loading.value = false
    }
  }

  // 上传文档
  const upload = async (file, knowledgeBase = null, roles = ['ADMIN', 'USER']) => {
    try {
      await uploadDocument(file, knowledgeBase || knowledgeBaseId.value.trim() || 'default', roles)
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
      await deleteDocument(id, knowledgeBaseId.value.trim() || 'default')
      await fetchDocuments()
      return true
    } catch (error) {
      console.error('删除失败:', error)
      return false
    }
  }

  const updatePermissions = async (id, roleCodes) => {
    await setDocumentPermissions(id, roleCodes)
    await fetchDocuments()
  }

  return {
    documents,
    loading,
    knowledgeBaseId,
    fetchDocuments,
    upload,
    remove,
    updatePermissions
  }
})
