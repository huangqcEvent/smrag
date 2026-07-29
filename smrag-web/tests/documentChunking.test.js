import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

import {
  DEFAULT_CHUNKING_STRATEGY,
  DOCUMENT_CHUNKING_STRATEGIES,
  chunkingStrategyDescription,
  chunkingStrategyLabel
} from '../src/constants/documentChunking.js'

const apiSource = readFileSync(
  new URL('../src/api/knowledge.js', import.meta.url),
  'utf8'
)
const storeSource = readFileSync(
  new URL('../src/stores/knowledge.js', import.meta.url),
  'utf8'
)
const viewSource = readFileSync(
  new URL('../src/views/Knowledge.vue', import.meta.url),
  'utf8'
)

test('token remains the default and both upload strategies are explained', () => {
  assert.equal(DEFAULT_CHUNKING_STRATEGY, 'TOKEN')
  assert.deepEqual(
    DOCUMENT_CHUNKING_STRATEGIES.map(item => item.value),
    ['TOKEN', 'RECURSIVE']
  )
  assert.equal(chunkingStrategyLabel('TOKEN'), 'Token 切块')
  assert.equal(chunkingStrategyLabel('RECURSIVE'), '递归切块')
  assert.match(chunkingStrategyDescription('RECURSIVE'), /段落|句子/)
  assert.equal(chunkingStrategyLabel('UNKNOWN'), '未知策略')
})

test('upload api and store forward the selected strategy', () => {
  assert.match(
    apiSource,
    /formData\.append\('chunkingStrategy',\s*chunkingStrategy\)/
  )
  assert.match(
    storeSource,
    /uploadDocument\(file,\s*code,\s*roles,\s*chunkingStrategy\)/
  )
})

test('knowledge view keeps the strategy selection and renders document labels', () => {
  assert.match(
    viewSource,
    /uploadChunkingStrategy\s*=\s*ref\(DEFAULT_CHUNKING_STRATEGY\)/
  )
  assert.match(viewSource, /v-model="uploadChunkingStrategy"/)
  assert.match(
    viewSource,
    /chunkingStrategyLabel\(row\.chunkingStrategy\)/
  )
  assert.match(
    viewSource,
    /uploadRoleCodes\.value,\s*uploadChunkingStrategy\.value/
  )
  const uploadHandler = viewSource.match(
    /const handleUpload = async \(\) => \{([\s\S]*?)\n\}/
  )?.[1] || ''
  assert.doesNotMatch(
    uploadHandler,
    /uploadChunkingStrategy\.value\s*=/
  )
})
