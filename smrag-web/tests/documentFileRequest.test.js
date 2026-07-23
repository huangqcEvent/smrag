import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const knowledgeApiSource = readFileSync(
  new URL('../src/api/knowledge.js', import.meta.url),
  'utf8'
)

test('document preview does not inherit the short default API timeout', () => {
  const requestConfig = knowledgeApiSource.match(
    /export function getDocumentFile\(id\) \{([\s\S]*?)\n\}/
  )?.[1] || ''

  assert.match(requestConfig, /responseType:\s*['"]blob['"]/)
  assert.match(requestConfig, /timeout:\s*120000/)
})
