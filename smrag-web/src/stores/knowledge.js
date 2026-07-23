import { defineStore } from 'pinia'
import { ref } from 'vue'
import {
  uploadDocument,
  getDocumentList,
  deleteDocument,
  setDocumentPermissions,
  getKnowledgeBases
} from '@/api/knowledge'
import {
  MAIN_KNOWLEDGE_BASE,
  normalizeKnowledgeBaseCode,
  normalizeKnowledgeBases,
  selectKnowledgeBaseCode
} from '@/constants/knowledgeBase'
import { resetKnowledgeAccountState } from './knowledgeAccountState'

export const useKnowledgeStore = defineStore('knowledge', () => {
  const documents = ref([])
  const loading = ref(false)
  const knowledgeBases = ref([MAIN_KNOWLEDGE_BASE])
  const knowledgeBaseId = ref(MAIN_KNOWLEDGE_BASE.code)
  let requestEpoch = 0

  const fetchKnowledgeBases = async () => {
    const requestId = ++requestEpoch
    try {
      const resources = normalizeKnowledgeBases(await getKnowledgeBases())
      if (requestId !== requestEpoch) return
      knowledgeBases.value = resources
    } catch (error) {
      if (requestId !== requestEpoch) return
      console.error('加载知识库失败:', error)
      knowledgeBases.value = [MAIN_KNOWLEDGE_BASE]
    }
    knowledgeBaseId.value = selectKnowledgeBaseCode(
      knowledgeBaseId.value,
      knowledgeBases.value
    )
  }

  // 获取文档列表
  const fetchDocuments = async () => {
    const requestId = ++requestEpoch
    const code = normalizeKnowledgeBaseCode(knowledgeBaseId.value)
    knowledgeBaseId.value = code
    loading.value = true
    try {
      const nextDocuments = await getDocumentList(code)
      if (requestId === requestEpoch && knowledgeBaseId.value === code) {
        documents.value = nextDocuments
      }
    } catch (error) {
      if (requestId === requestEpoch) {
        documents.value = []
      }
      throw error
    } finally {
      if (requestId === requestEpoch) {
        loading.value = false
      }
    }
  }

  const resetForAccountChange = () => {
    const state = {
      documents: documents.value,
      loading: loading.value,
      knowledgeBases: knowledgeBases.value,
      knowledgeBaseId: knowledgeBaseId.value,
      requestEpoch
    }
    resetKnowledgeAccountState(state)
    documents.value = state.documents
    loading.value = state.loading
    knowledgeBases.value = state.knowledgeBases
    knowledgeBaseId.value = state.knowledgeBaseId
    requestEpoch = state.requestEpoch
  }

  // 上传文档
  const upload = async (file, knowledgeBase = null, roles = ['ADMIN', 'USER']) => {
    try {
      const code = normalizeKnowledgeBaseCode(knowledgeBase || knowledgeBaseId.value)
      await uploadDocument(file, code, roles)
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
      await deleteDocument(id, normalizeKnowledgeBaseCode(knowledgeBaseId.value))
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
    knowledgeBases,
    knowledgeBaseId,
    fetchKnowledgeBases,
    fetchDocuments,
    upload,
    remove,
    updatePermissions,
    resetForAccountChange
  }
})
