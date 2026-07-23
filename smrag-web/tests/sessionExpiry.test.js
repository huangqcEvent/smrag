import test from 'node:test'
import assert from 'node:assert/strict'
import { handleSessionExpiry } from '../src/utils/sessionExpiry.js'

test('logs out and redirects when a Fetch request returns 401', () => {
  const actions = []

  assert.equal(handleSessionExpiry({ code: 401, requestToken: 'token-a' }, {
    currentToken: 'token-a',
    expireSession: () => actions.push('expired')
  }), true)
  assert.deepEqual(actions, ['expired'])
})

test('does not expire a new account when an old request returns 401', () => {
  const actions = []
  const staleError = { code: 401, requestToken: 'old-token' }

  assert.equal(handleSessionExpiry(staleError, {
    currentToken: 'new-token',
    expireSession: () => actions.push('expired')
  }), false)
  assert.deepEqual(actions, [])
  assert.equal(staleError.suppressUserFeedback, true)
})

test('does nothing for a non-401 failure', () => {
  const actions = []

  assert.equal(handleSessionExpiry({ code: 500, requestToken: 'token-a' }, {
    currentToken: 'token-a',
    expireSession: () => actions.push('expired')
  }), false)
  assert.deepEqual(actions, [])
})
