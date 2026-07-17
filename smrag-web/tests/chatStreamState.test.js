import assert from 'node:assert/strict'
import test from 'node:test'
import {
  applyChatStreamEvent,
  createPendingChatMessage
} from '../src/stores/chatStreamState.js'

test('updates one pending message through the complete stream lifecycle', () => {
  const message = createPendingChatMessage('什么是 RAG？', 'session-1', '2026-07-17T12:00:00.000Z')
  const original = message

  applyChatStreamEvent(message, { type: 'metadata', sessionId: 'session-1', routeType: 'KNOWLEDGE_QA' })
  applyChatStreamEvent(message, { type: 'status', phase: 'RETRIEVING' })
  applyChatStreamEvent(message, { type: 'delta', content: '检索增强' })
  applyChatStreamEvent(message, { type: 'delta', content: '生成。' })
  applyChatStreamEvent(message, {
    type: 'citations',
    citations: [{ vectorId: 'v1', documentName: 'RAG 指南' }],
    references: ['RAG 指南']
  })
  applyChatStreamEvent(message, { type: 'done' })

  assert.equal(message, original)
  assert.equal(message.answer, '检索增强生成。')
  assert.equal(message.routeType, 'KNOWLEDGE_QA')
  assert.equal(message.phase, 'DONE')
  assert.equal(message.state, 'done')
  assert.deepEqual(message.references, ['RAG 指南'])
  assert.equal(message.citations.length, 1)
})

test('keeps received text when a stream fails or is stopped', () => {
  const failed = createPendingChatMessage('问题', 'session-1')
  applyChatStreamEvent(failed, { type: 'delta', content: '部分回答' })
  applyChatStreamEvent(failed, { type: 'error', message: '生成失败' })

  assert.equal(failed.answer, '部分回答')
  assert.equal(failed.error, '生成失败')
  assert.equal(failed.state, 'error')

  const stopped = createPendingChatMessage('问题', 'session-2')
  applyChatStreamEvent(stopped, { type: 'delta', content: '已生成内容' })
  applyChatStreamEvent(stopped, { type: 'stopped' })

  assert.equal(stopped.answer, '已生成内容')
  assert.equal(stopped.error, '')
  assert.equal(stopped.state, 'stopped')
})

test('does not let stop or late deltas overwrite a terminal state', () => {
  const message = createPendingChatMessage('问题', 'session-1')
  applyChatStreamEvent(message, { type: 'delta', content: '完整回答' })
  applyChatStreamEvent(message, { type: 'done' })
  applyChatStreamEvent(message, { type: 'stopped' })
  applyChatStreamEvent(message, { type: 'delta', content: '迟到内容' })

  assert.equal(message.state, 'done')
  assert.equal(message.answer, '完整回答')
})
