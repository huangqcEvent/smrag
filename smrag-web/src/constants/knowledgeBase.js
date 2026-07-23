export const MAIN_KNOWLEDGE_BASE = Object.freeze({
  code: 'company-main',
  name: '企业知识库',
  description: '企业内部统一知识库'
})

export function normalizeKnowledgeBaseCode(value) {
  const normalized = String(value ?? '').trim().toLowerCase()
  return !normalized || normalized === 'default'
    ? MAIN_KNOWLEDGE_BASE.code
    : normalized
}

export function normalizeKnowledgeBases(value) {
  if (!Array.isArray(value)) return [MAIN_KNOWLEDGE_BASE]

  const seen = new Set()
  const resources = value.flatMap(item => {
    if (!item || !String(item.code ?? '').trim()) return []
    const code = normalizeKnowledgeBaseCode(item.code)
    if (seen.has(code)) return []
    seen.add(code)
    return [{
      code,
      name: String(item.name ?? '').trim() || code,
      description: String(item.description ?? '').trim()
    }]
  })

  return resources.length ? resources : [MAIN_KNOWLEDGE_BASE]
}

export function selectKnowledgeBaseCode(current, knowledgeBases) {
  const normalized = normalizeKnowledgeBaseCode(current)
  return knowledgeBases.some(item => item.code === normalized)
    ? normalized
    : (knowledgeBases[0]?.code || MAIN_KNOWLEDGE_BASE.code)
}
