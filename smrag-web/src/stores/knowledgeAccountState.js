import { MAIN_KNOWLEDGE_BASE } from '../constants/knowledgeBase.js'

export function resetKnowledgeAccountState(state) {
  state.documents = []
  state.loading = false
  state.knowledgeBases = [MAIN_KNOWLEDGE_BASE]
  state.knowledgeBaseId = MAIN_KNOWLEDGE_BASE.code
  state.requestEpoch += 1
}
