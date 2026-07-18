import test from 'node:test'
import assert from 'node:assert/strict'

import { resetChatAccountState } from '../src/stores/chatAccountState.js'
import { shouldAutoScroll, resetScrollAfterHistoryLoad } from '../src/utils/chatScrollState.js'

test('clears every account-scoped chat value and aborts an active stream', () => {
  let aborted = false
  const state = {
    currentSessionId: 'old-session',
    chatHistory: [{ question: 'old' }],
    sessionList: [{ sessionId: 'old-session' }],
    loading: true
  }

  resetChatAccountState(state, { abort: () => { aborted = true } })

  assert.equal(aborted, true)
  assert.deepEqual(state, {
    currentSessionId: '',
    chatHistory: [],
    sessionList: [],
    loading: false
  })
})

test('history load resets scroll state to the bottom', () => {
  assert.deepEqual(resetScrollAfterHistoryLoad(), {
    isNearBottom: true,
    showScrollButton: false
  })
})

test('new stream content only auto-scrolls while the user remains near the bottom', () => {
  assert.equal(shouldAutoScroll(true), true)
  assert.equal(shouldAutoScroll(false), false)
})
