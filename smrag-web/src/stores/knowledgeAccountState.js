export function resetKnowledgeAccountState(state) {
  state.documents = []
  state.loading = false
  state.knowledgeBases = []
  state.adminKnowledgeBases = []
  state.knowledgeBaseCode = ''
  state.selectedKnowledgeBaseCodes = []
  state.accountEpoch += 1
  state.documentRequestEpoch += 1
  state.adminCatalogRequested = false
}
