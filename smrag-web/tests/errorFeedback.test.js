import test from 'node:test'
import assert from 'node:assert/strict'
import {
  isSilentError,
  toUserErrorMessage
} from '../src/utils/safeError.js'
import { createErrorNotifier } from '../src/utils/errorFeedback.js'

test('uses the operation fallback for generic server and Axios errors', () => {
  assert.equal(toUserErrorMessage({
    response: {
      status: 500,
      data: { message: '系统繁忙，请稍后重试' }
    },
    message: 'Request failed with status code 500'
  }, '保存失败'), '保存失败')
})

test('keeps a safe business error returned by the server', () => {
  assert.equal(toUserErrorMessage({
    response: {
      status: 403,
      data: { message: '没有文档权限' }
    }
  }), '没有文档权限')
})

test('maps timeouts and network failures to actionable messages', () => {
  assert.equal(toUserErrorMessage({
    code: 'ECONNABORTED',
    message: 'timeout of 30000ms exceeded'
  }), '请求超时，请检查网络连接')

  assert.equal(toUserErrorMessage({
    code: 'ERR_NETWORK',
    message: 'Network Error'
  }), '网络连接异常，请检查网络')
})

test('does not expose technical exception details', () => {
  assert.equal(toUserErrorMessage(
    new Error('org.postgresql.util.PSQLException: SQL failed'),
    '加载文档失败'
  ), '加载文档失败')
})

test('uses stable status fallbacks when the server has no business message', () => {
  assert.equal(toUserErrorMessage({
    response: { status: 401, data: {} }
  }), '登录已过期，请重新登录')
  assert.equal(toUserErrorMessage({
    response: { status: 404, data: {} }
  }), '请求的内容不存在或已被删除')
  assert.equal(toUserErrorMessage({
    response: { status: 409, data: {} }
  }), '数据已发生变化，请刷新页面')
})

test('keeps a safe login failure instead of treating it as an expired session', () => {
  assert.equal(toUserErrorMessage({
    config: { skipAuthRedirect: true },
    response: {
      status: 401,
      data: { message: '用户名或密码错误' }
    }
  }, '登录失败'), '用户名或密码错误')
})

test('treats cancellations and aborted requests as silent', () => {
  assert.equal(isSilentError('cancel'), true)
  assert.equal(isSilentError('close'), true)
  assert.equal(isSilentError({ name: 'AbortError' }), true)
  assert.equal(isSilentError(new Error('failed')), false)
})

test('notifies the same error object only once and ignores aborts', () => {
  const messages = []
  const notify = createErrorNotifier(message => messages.push(message))
  const error = new Error('Request failed with status code 500')

  assert.equal(notify(error, '保存失败'), '保存失败')
  assert.equal(notify(error, '保存失败'), '')
  assert.equal(notify({ name: 'AbortError' }, '保存失败'), '')
  assert.deepEqual(messages, ['保存失败'])
})
