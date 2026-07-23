import { defineStore } from 'pinia'
import { ref } from 'vue'
import {
  uploadDocument,
  getDocumentList,
  deleteDocument,
  setDocumentPermissions
} from '@/api/knowledge'
import {
  changeKnowledgeBaseStatus,
  createKnowledgeBase,
  deleteKnowledgeBase,
  getAccessibleKnowledgeBases,
  getAdminKnowledgeBases,
  moveDocument,
  replaceKnowledgeBaseRoles,
  updateKnowledgeBase
} from '@/api/knowledgeBase'
import {
  normalizeKnowledgeBases,
  reconcileKnowledgeBaseSelection,
  selectManagementKnowledgeBaseCode,
  validateKnowledgeBaseSelection
} from '@/constants/knowledgeBase'
import { resetKnowledgeAccountState } from './knowledgeAccountState'

export const useKnowledgeStore = defineStore('knowledge', () => {
  const documents = ref([])
  const loading = ref(false)
  const knowledgeBases = ref([])
  const adminKnowledgeBases = ref([])
  const knowledgeBaseCode = ref('')
  const selectedKnowledgeBaseCodes = ref([])
  let accountEpoch = 0
  let documentRequestEpoch = 0
  let adminCatalogRequested = false

  const reconcileManagementSelection = () => {
    knowledgeBaseCode.value = selectManagementKnowledgeBaseCode(
      knowledgeBaseCode.value,
      knowledgeBases.value,
      adminKnowledgeBases.value,
      adminCatalogRequested
    )
  }

  const fetchKnowledgeBases = async () => {
    const requestAccountEpoch = accountEpoch
    const resources = normalizeKnowledgeBases(await getAccessibleKnowledgeBases())
    if (requestAccountEpoch !== accountEpoch) return
    knowledgeBases.value = resources
    reconcileManagementSelection()
    selectedKnowledgeBaseCodes.value = reconcileKnowledgeBaseSelection(
      selectedKnowledgeBaseCodes.value,
      resources
    )
  }

  const fetchAdminKnowledgeBases = async () => {
    adminCatalogRequested = true
    const requestAccountEpoch = accountEpoch
    const resources = normalizeKnowledgeBases(await getAdminKnowledgeBases())
    if (requestAccountEpoch !== accountEpoch) return
    adminKnowledgeBases.value = resources
    reconcileManagementSelection()
  }

  const fetchDocuments = async (code = knowledgeBaseCode.value) => {
    if (!code) {
      documents.value = []
      return
    }
    const requestAccountEpoch = accountEpoch
    const requestId = ++documentRequestEpoch
    knowledgeBaseCode.value = code
    loading.value = true
    try {
      const next = await getDocumentList(code)
      if (requestAccountEpoch === accountEpoch
        && requestId === documentRequestEpoch
        && knowledgeBaseCode.value === code) {
        documents.value = Array.isArray(next) ? next : []
      }
    } finally {
      if (requestAccountEpoch === accountEpoch
        && requestId === documentRequestEpoch) loading.value = false
    }
  }

  const upload = async (file, code, roles) => {
    await uploadDocument(file, code, roles)
    await fetchDocuments(code)
  }

  const remove = async id => {
    await deleteDocument(id, knowledgeBaseCode.value)
    await fetchDocuments()
    await fetchAdminKnowledgeBases()
  }

  const updatePermissions = async (id, roleCodes) => {
    await setDocumentPermissions(id, roleCodes)
    await fetchDocuments()
  }

  const createBase = async data => {
    await createKnowledgeBase(data)
    await Promise.all([fetchAdminKnowledgeBases(), fetchKnowledgeBases()])
  }

  const updateBase = async (id, data) => {
    await updateKnowledgeBase(id, data)
    await Promise.all([fetchAdminKnowledgeBases(), fetchKnowledgeBases()])
  }

  const updateBaseRoles = async (id, data) => {
    await replaceKnowledgeBaseRoles(id, data)
    await Promise.all([fetchAdminKnowledgeBases(), fetchKnowledgeBases()])
  }

  const updateBaseStatus = async (id, data) => {
    await changeKnowledgeBaseStatus(id, data)
    await Promise.all([fetchAdminKnowledgeBases(), fetchKnowledgeBases()])
  }

  const removeBase = async id => {
    await deleteKnowledgeBase(id)
    await Promise.all([fetchAdminKnowledgeBases(), fetchKnowledgeBases()])
  }

  const transferDocument = async (id, targetCode) => {
    await moveDocument(id, targetCode)
    await fetchDocuments()
    await fetchAdminKnowledgeBases()
  }

  const setChatSelection = values => {
    selectedKnowledgeBaseCodes.value =
      validateKnowledgeBaseSelection(values)
  }

  const resetForAccountChange = () => {
    const state = {
      documents: documents.value,
      loading: loading.value,
      knowledgeBases: knowledgeBases.value,
      adminKnowledgeBases: adminKnowledgeBases.value,
      knowledgeBaseCode: knowledgeBaseCode.value,
      selectedKnowledgeBaseCodes: selectedKnowledgeBaseCodes.value,
      accountEpoch,
      documentRequestEpoch,
      adminCatalogRequested
    }
    resetKnowledgeAccountState(state)
    documents.value = state.documents
    loading.value = state.loading
    knowledgeBases.value = state.knowledgeBases
    adminKnowledgeBases.value = state.adminKnowledgeBases
    knowledgeBaseCode.value = state.knowledgeBaseCode
    selectedKnowledgeBaseCodes.value = state.selectedKnowledgeBaseCodes
    accountEpoch = state.accountEpoch
    documentRequestEpoch = state.documentRequestEpoch
    adminCatalogRequested = state.adminCatalogRequested
  }

  return {
    documents,
    loading,
    knowledgeBases,
    adminKnowledgeBases,
    knowledgeBaseCode,
    selectedKnowledgeBaseCodes,
    fetchKnowledgeBases,
    fetchAdminKnowledgeBases,
    fetchDocuments,
    upload,
    remove,
    updatePermissions,
    createBase,
    updateBase,
    updateBaseRoles,
    updateBaseStatus,
    removeBase,
    transferDocument,
    setChatSelection,
    resetForAccountChange
  }
})
