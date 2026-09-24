import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

import { MAX_UPLOAD_SIZE_BYTES } from '../src/constants/uploadLimits.js'
import { validateUploadFile } from '../src/utils/uploadGuard.js'

// 后端口径：smrag.rag.max-upload-size-bytes（RagProperties#maxUploadSizeBytes）
const EXPECTED_LIMIT = 20971520
const MEGABYTE = 1024 * 1024

const viewSource = readFileSync(
  new URL('../src/views/Knowledge.vue', import.meta.url),
  'utf8'
)

test('accepts a file exactly at the size limit', () => {
  assert.equal(MAX_UPLOAD_SIZE_BYTES, EXPECTED_LIMIT)

  const result = validateUploadFile({
    name: 'exactly-at-limit.pdf',
    size: MAX_UPLOAD_SIZE_BYTES
  })

  assert.equal(result.ok, true)
  assert.equal(result.code, null)
  assert.equal(result.message, '')
})

test('accepts a small document', () => {
  const result = validateUploadFile({ name: 'small.txt', size: 2048 })

  assert.equal(result.ok, true)
  assert.equal(result.code, null)
})

test('rejects a file one byte over the limit and explains the limit in megabytes', () => {
  const result = validateUploadFile({
    name: 'too-large.pdf',
    size: MAX_UPLOAD_SIZE_BYTES + 1
  })

  assert.equal(result.ok, false)
  assert.equal(result.code, 'TOO_LARGE')
  assert.match(result.message, /20MB/)
})

test('rejects a file far over the limit', () => {
  const result = validateUploadFile({ name: 'huge.pdf', size: 200 * MEGABYTE })

  assert.equal(result.ok, false)
  assert.equal(result.code, 'TOO_LARGE')
})

test('rejects an empty file', () => {
  const result = validateUploadFile({ name: 'empty.pdf', size: 0 })

  assert.equal(result.ok, false)
  assert.equal(result.code, 'EMPTY')
  assert.ok(result.message.length > 0)
})

test('rejects a missing file', () => {
  for (const value of [null, undefined, {}, { name: 'x.pdf' }]) {
    const result = validateUploadFile(value)
    assert.equal(result.ok, false, `expected rejection for ${JSON.stringify(value)}`)
  }
})

test('keeps the empty check ahead of the size check like the backend does', () => {
  assert.equal(validateUploadFile({ name: 'empty.pdf', size: 0 }).code, 'EMPTY')
  assert.equal(
    validateUploadFile({ name: 'big-empty.pdf', size: -1 }).code,
    'EMPTY'
  )
})

test('wires the guard into the knowledge upload view', () => {
  assert.match(viewSource, /validateUploadFile/)
  assert.match(viewSource, /from '@\/utils\/uploadGuard'/)
})
