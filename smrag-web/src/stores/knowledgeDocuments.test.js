import test from 'node:test'
import assert from 'node:assert/strict'

import {
  DEFAULT_DOCUMENT_PAGE_SIZE,
  mergeDocumentPage,
  normalizeDocumentFilters
} from './knowledgeDocuments.js'

test('normalizeDocumentFilters trims keyword and normalizes status', () => {
  assert.deepEqual(
    normalizeDocumentFilters({
      keyword: '  report  ',
      status: 'completed'
    }),
    {
      keyword: 'report',
      status: 'COMPLETED'
    }
  )
})

test('mergeDocumentPage replaces the list on first page and appends unique records later', () => {
  const firstPage = mergeDocumentPage([], {
    records: [{ id: 1, name: 'A' }, { id: 2, name: 'B' }],
    total: 5,
    pageNum: 1,
    pageSize: DEFAULT_DOCUMENT_PAGE_SIZE,
    hasMore: true
  })

  assert.deepEqual(firstPage.documents, [
    { id: 1, name: 'A' },
    { id: 2, name: 'B' }
  ])
  assert.equal(firstPage.page.pageNum, 1)
  assert.equal(firstPage.page.hasMore, true)

  const secondPage = mergeDocumentPage(firstPage.documents, {
    records: [{ id: 2, name: 'B' }, { id: 3, name: 'C' }],
    total: 5,
    pageNum: 2,
    pageSize: DEFAULT_DOCUMENT_PAGE_SIZE,
    hasMore: true
  })

  assert.deepEqual(secondPage.documents, [
    { id: 1, name: 'A' },
    { id: 2, name: 'B' },
    { id: 3, name: 'C' }
  ])
  assert.equal(secondPage.page.pageNum, 2)
  assert.equal(secondPage.page.total, 5)
})
