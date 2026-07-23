import test from 'node:test'
import assert from 'node:assert/strict'

import {
  limitKnowledgeBaseSelection,
  normalizeKnowledgeBaseCode,
  normalizeKnowledgeBases,
  reconcileKnowledgeBaseSelection,
  selectManagementKnowledgeBaseCode,
  selectKnowledgeBaseCode,
  validateKnowledgeBaseSelection
} from '../src/constants/knowledgeBase.js'
import * as knowledgeBasePolicy from '../src/constants/knowledgeBase.js'
import { resetKnowledgeAccountState } from '../src/stores/knowledgeAccountState.js'

test('keeps an empty server response empty without inventing a default base', () => {
  assert.deepEqual(normalizeKnowledgeBases(null), [])
  assert.equal(normalizeKnowledgeBaseCode(' Research '), 'research')
  assert.equal(selectKnowledgeBaseCode('missing', []), '')
})

test('normalizes resources and removes duplicate codes', () => {
  assert.deepEqual(normalizeKnowledgeBases([
    { id: 1, code: 'research', name: '投研库' },
    { id: 2, code: ' RESEARCH ', name: '重复项' },
    { code: '', name: '坏数据' }
  ]), [{
    id: 1,
    code: 'research',
    name: '投研库',
    description: '',
    status: 'ACTIVE',
    roles: []
  }])
})

test('validates the explicit one to five knowledge base chat scope', () => {
  assert.deepEqual(limitKnowledgeBaseSelection(['a', 'b', 'a']), ['a', 'b'])
  assert.throws(() => validateKnowledgeBaseSelection([]))
  assert.throws(() => validateKnowledgeBaseSelection(['a', 'b', 'c', 'd', 'e', 'f']))
  assert.deepEqual(
    reconcileKnowledgeBaseSelection(['a', 'missing'], [{ code: 'a' }]),
    ['a']
  )
})

test('keeps an administrator on a disabled base while accessible bases refresh', () => {
  const accessible = [{ code: 'active' }]
  const admin = [{ code: 'active' }, { code: 'disabled', status: 'DISABLED' }]

  assert.equal(
    selectManagementKnowledgeBaseCode('disabled', accessible, admin, true),
    'disabled'
  )
})

test('clears all account-scoped knowledge values', () => {
  const state = {
    documents: [{ id: 1 }],
    loading: true,
    knowledgeBases: [{ code: 'a' }],
    adminKnowledgeBases: [{ code: 'b' }],
    knowledgeBaseCode: 'a',
    selectedKnowledgeBaseCodes: ['a'],
    accountEpoch: 2,
    documentRequestEpoch: 4,
    adminCatalogRequested: true
  }
  resetKnowledgeAccountState(state)
  assert.deepEqual(state, {
    documents: [],
    loading: false,
    knowledgeBases: [],
    adminKnowledgeBases: [],
    knowledgeBaseCode: '',
    selectedKnowledgeBaseCodes: [],
    accountEpoch: 3,
    documentRequestEpoch: 5,
    adminCatalogRequested: false
  })
})

test('excludes the global administrator from configurable access roles', () => {
  assert.equal(
    typeof knowledgeBasePolicy.filterConfigurableAccessRoles,
    'function'
  )
  assert.deepEqual(
    knowledgeBasePolicy.filterConfigurableAccessRoles([
      { id: 1, roleCode: 'ADMIN' },
      { id: 2, roleCode: 'USER' },
      { id: 3, roleCode: 'AUDITOR' }
    ]).map(role => role.roleCode),
    ['USER', 'AUDITOR']
  )
})
