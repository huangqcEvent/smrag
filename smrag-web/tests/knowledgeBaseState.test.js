import test from 'node:test'
import assert from 'node:assert/strict'

import {
  MAIN_KNOWLEDGE_BASE,
  normalizeKnowledgeBaseCode,
  normalizeKnowledgeBases,
  selectKnowledgeBaseCode
} from '../src/constants/knowledgeBase.js'
import { resetKnowledgeAccountState } from '../src/stores/knowledgeAccountState.js'

test('normalizes blank and legacy default codes to company-main', () => {
  assert.equal(normalizeKnowledgeBaseCode(), 'company-main')
  assert.equal(normalizeKnowledgeBaseCode('  '), 'company-main')
  assert.equal(normalizeKnowledgeBaseCode('default'), 'company-main')
  assert.equal(normalizeKnowledgeBaseCode(' FINANCE '), 'finance')
})

test('normalizes API resources and falls back to the company knowledge base', () => {
  assert.deepEqual(normalizeKnowledgeBases(null), [MAIN_KNOWLEDGE_BASE])
  assert.deepEqual(normalizeKnowledgeBases([
    { code: 'company-main', name: '研发知识中心', description: '后台配置名称' },
    { code: '', name: '坏数据' }
  ]), [{
    code: 'company-main',
    name: '研发知识中心',
    description: '后台配置名称'
  }])
})

test('keeps a valid selection and otherwise selects the first available resource', () => {
  const bases = [
    MAIN_KNOWLEDGE_BASE,
    { code: 'finance', name: '投研知识库', description: '' }
  ]

  assert.equal(selectKnowledgeBaseCode('finance', bases), 'finance')
  assert.equal(selectKnowledgeBaseCode('missing', bases), 'company-main')
  assert.equal(selectKnowledgeBaseCode('default', bases), 'company-main')
})

test('clears every account-scoped knowledge value and invalidates in-flight requests', () => {
  const state = {
    documents: [{ id: 1, name: '上一账号文档' }],
    loading: true,
    knowledgeBases: [{ code: 'finance', name: '财务知识库', description: '' }],
    knowledgeBaseId: 'finance',
    requestEpoch: 2
  }

  resetKnowledgeAccountState(state)

  assert.deepEqual(state, {
    documents: [],
    loading: false,
    knowledgeBases: [MAIN_KNOWLEDGE_BASE],
    knowledgeBaseId: MAIN_KNOWLEDGE_BASE.code,
    requestEpoch: 3
  })
})
