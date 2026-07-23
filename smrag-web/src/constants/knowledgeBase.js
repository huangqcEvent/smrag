export const MAX_CHAT_KNOWLEDGE_BASES = 5

export function normalizeKnowledgeBaseCode(value) {
  return String(value ?? '').trim().toLowerCase()
}

export function normalizeKnowledgeBases(value) {
  if (!Array.isArray(value)) return []
  const seen = new Set()
  return value.flatMap(item => {
    const code = normalizeKnowledgeBaseCode(item?.code)
    if (!code || seen.has(code)) return []
    seen.add(code)
    return [{
      ...item,
      code,
      name: String(item?.name ?? '').trim() || code,
      description: String(item?.description ?? '').trim(),
      status: item?.status || 'ACTIVE',
      roles: Array.isArray(item?.roles) ? item.roles : []
    }]
  })
}

export function selectKnowledgeBaseCode(current, knowledgeBases) {
  const normalized = normalizeKnowledgeBaseCode(current)
  return knowledgeBases.some(item => item.code === normalized)
    ? normalized
    : (knowledgeBases[0]?.code || '')
}

export function selectManagementKnowledgeBaseCode(
  current,
  accessibleKnowledgeBases,
  adminKnowledgeBases,
  adminCatalogRequested
) {
  const source = adminCatalogRequested
    ? (adminKnowledgeBases || [])
    : (accessibleKnowledgeBases || [])
  return selectKnowledgeBaseCode(current, source)
}

export function limitKnowledgeBaseSelection(
  values,
  limit = MAX_CHAT_KNOWLEDGE_BASES
) {
  return [...new Set((values || [])
    .map(normalizeKnowledgeBaseCode)
    .filter(Boolean))].slice(0, limit)
}

export function validateKnowledgeBaseSelection(
  values,
  limit = MAX_CHAT_KNOWLEDGE_BASES
) {
  const normalized = limitKnowledgeBaseSelection(values, Number.MAX_SAFE_INTEGER)
  if (normalized.length === 0) {
    throw new Error('请至少选择一个知识库')
  }
  if (normalized.length > limit) {
    throw new Error(`一次最多选择 ${limit} 个知识库`)
  }
  return normalized
}

export function reconcileKnowledgeBaseSelection(values, knowledgeBases) {
  const allowed = new Set((knowledgeBases || []).map(item => item.code))
  return limitKnowledgeBaseSelection(values)
    .filter(code => allowed.has(code))
}
