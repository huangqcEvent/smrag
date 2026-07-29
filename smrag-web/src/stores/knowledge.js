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
import {
  createDocumentPageState,
  mergeDocumentPage,
  normalizeDocumentFilters
} from './knowledgeDocuments'

export const useKnowledgeStore = defineStore('knowledge', () => {
  const documents = ref([])
  const loading = ref(false)
  const loadingMoreDocuments = ref(false)
  const knowledgeBases = ref([])
  const adminKnowledgeBases = ref([])
  const knowledgeBaseCode = ref('')
  const documentFilters = ref(normalizeDocumentFilters())
  const documentPage = ref(createDocumentPageState())
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

  const resetDocumentState = () => {
    documents.value = []
    documentPage.value = createDocumentPageState()
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

  const fetchDocuments = async (code = knowledgeBaseCode.value, options = {}) => {
    if (!code) {
      resetDocumentState()
      return
    }

    const append = options.append === true
    const normalizedFilters = normalizeDocumentFilters(
      options.filters ?? documentFilters.value
    )
    const pageNum = Number.isInteger(options.pageNum) && options.pageNum > 0
      ? options.pageNum
      : append
        ? documentPage.value.pageNum + 1
        : 1
    const requestAccountEpoch = accountEpoch
    const requestId = ++documentRequestEpoch
    knowledgeBaseCode.value = code
    if (append) {
      loadingMoreDocuments.value = true
    } else {
      loading.value = true
    }
    try {
      const next = await getDocumentList({
        knowledgeBaseCode: code,
        pageNum,
        pageSize: documentPage.value.pageSize,
        ...normalizedFilters
      })
      if (requestAccountEpoch === accountEpoch
        && requestId === documentRequestEpoch
        && knowledgeBaseCode.value === code) {
        documentFilters.value = normalizedFilters
        const merged = mergeDocumentPage(
          append ? documents.value : [],
          next
        )
        documents.value = merged.documents
        documentPage.value = merged.page
      }
    } finally {
      if (requestAccountEpoch === accountEpoch
        && requestId === documentRequestEpoch) {
        loading.value = false
        loadingMoreDocuments.value = false
      }
    }
  }

  const refreshDocuments = async (
    code = knowledgeBaseCode.value,
    filters = documentFilters.value
  ) => {
    await fetchDocuments(code, { filters, pageNum: 1, append: false })
  }

  const loadMoreDocuments = async () => {
    if (!knowledgeBaseCode.value
      || loading.value
      || loadingMoreDocuments.value
      || !documentPage.value.hasMore) {
      return
    }
    await fetchDocuments(knowledgeBaseCode.value, { append: true })
  }

  const setDocumentFilters = async filters => {
    const nextFilters = normalizeDocumentFilters(filters)
    documentFilters.value = nextFilters
    await refreshDocuments(knowledgeBaseCode.value, nextFilters)
  }

  const upload = async (file, code, roles, chunkingStrategy) => {
    await uploadDocument(file, code, roles, chunkingStrategy)
    await refreshDocuments(code)
  }

  const remove = async id => {
    await deleteDocument(id, knowledgeBaseCode.value)
    await refreshDocuments()
    await fetchAdminKnowledgeBases()
  }

  const updatePermissions = async (id, roleCodes) => {
    await setDocumentPermissions(id, roleCodes)
    await refreshDocuments()
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
    await refreshDocuments()
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
      loadingMoreDocuments: loadingMoreDocuments.value,
      knowledgeBases: knowledgeBases.value,
      adminKnowledgeBases: adminKnowledgeBases.value,
      knowledgeBaseCode: knowledgeBaseCode.value,
      documentFilters: documentFilters.value,
      documentPage: documentPage.value,
      selectedKnowledgeBaseCodes: selectedKnowledgeBaseCodes.value,
      accountEpoch,
      documentRequestEpoch,
      adminCatalogRequested
    }
    resetKnowledgeAccountState(state)
    documents.value = state.documents
    loading.value = state.loading
    loadingMoreDocuments.value = state.loadingMoreDocuments
    knowledgeBases.value = state.knowledgeBases
    adminKnowledgeBases.value = state.adminKnowledgeBases
    knowledgeBaseCode.value = state.knowledgeBaseCode
    documentFilters.value = state.documentFilters
    documentPage.value = state.documentPage
    selectedKnowledgeBaseCodes.value = state.selectedKnowledgeBaseCodes
    accountEpoch = state.accountEpoch
    documentRequestEpoch = state.documentRequestEpoch
    adminCatalogRequested = state.adminCatalogRequested
  }

  return {
    documents,
    loading,
    loadingMoreDocuments,
    knowledgeBases,
    adminKnowledgeBases,
    knowledgeBaseCode,
    documentFilters,
    documentPage,
    selectedKnowledgeBaseCodes,
    fetchKnowledgeBases,
    fetchAdminKnowledgeBases,
    fetchDocuments,
    refreshDocuments,
    loadMoreDocuments,
    setDocumentFilters,
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
