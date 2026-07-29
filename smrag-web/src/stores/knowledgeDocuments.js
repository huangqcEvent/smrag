export const DEFAULT_DOCUMENT_PAGE_SIZE = 20

export function createDocumentPageState(overrides = {}) {
  return {
    pageNum: 1,
    pageSize: DEFAULT_DOCUMENT_PAGE_SIZE,
    total: 0,
    hasMore: false,
    ...overrides
  }
}

export function normalizeDocumentFilters(filters = {}) {
  return {
    keyword: typeof filters.keyword === 'string'
      ? filters.keyword.trim()
      : '',
    status: typeof filters.status === 'string'
      ? filters.status.trim().toUpperCase()
      : ''
  }
}

export function mergeDocumentPage(currentDocuments = [], response = {}) {
  const records = Array.isArray(response?.records)
    ? response.records
    : Array.isArray(response)
      ? response
      : []
  const pageNum = toPositiveInteger(response?.pageNum, 1)
  const pageSize = toPositiveInteger(response?.pageSize, DEFAULT_DOCUMENT_PAGE_SIZE)
  const total = Number.isFinite(Number(response?.total))
    ? Number(response.total)
    : records.length
  const documents = pageNum <= 1
    ? records
    : dedupeDocumentsById([...currentDocuments, ...records])
  return {
    documents,
    page: createDocumentPageState({
      pageNum,
      pageSize,
      total,
      hasMore: Boolean(response?.hasMore)
    })
  }
}

function dedupeDocumentsById(documents) {
  const seen = new Set()
  return documents.filter(document => {
    if (document?.id == null) return true
    if (seen.has(document.id)) return false
    seen.add(document.id)
    return true
  })
}

function toPositiveInteger(value, fallback) {
  const normalized = Number(value)
  return Number.isInteger(normalized) && normalized > 0 ? normalized : fallback
}
