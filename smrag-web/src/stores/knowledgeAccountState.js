export function resetKnowledgeAccountState(state) {
  state.documents = []
  state.loading = false
  state.loadingMoreDocuments = false
  state.knowledgeBases = []
  state.adminKnowledgeBases = []
  state.knowledgeBaseCode = ''
  state.documentFilters = { keyword: '', status: '' }
  state.documentPage = {
    pageNum: 1,
    pageSize: 20,
    total: 0,
    hasMore: false
  }
  state.selectedKnowledgeBaseCodes = []
  state.accountEpoch += 1
  state.documentRequestEpoch += 1
  state.adminCatalogRequested = false
}
