import assert from 'node:assert/strict'
import test from 'node:test'
import { postChatStream } from '../src/api/chatStream.js'
import { createSseParser } from '../src/utils/sse.js'

test('parses CRLF frames and joins multiline data fields', () => {
  const events = []
  const parser = createSseParser(event => events.push(event))

  parser.feed('event: delta\r\ndata: first line\r\ndata: second line\r\n\r\n')

  assert.deepEqual(events, [{ event: 'delta', data: 'first line\nsecond line' }])
})

test('keeps an event intact when separators and fields cross chunks', () => {
  const events = []
  const parser = createSseParser(event => events.push(event))

  parser.feed('event: meta')
  parser.feed('data\ndata: {"session')
  parser.feed('Id":"session-1"}\n')
  parser.feed('\nevent: delta\nda')
  parser.feed('ta: {"content":"你')
  parser.feed('好"}\n\n')

  assert.deepEqual(events, [
    { event: 'metadata', data: '{"sessionId":"session-1"}' },
    { event: 'delta', data: '{"content":"你好"}' }
  ])
})

test('flushes the final event without a trailing blank line', () => {
  const events = []
  const parser = createSseParser(event => events.push(event))

  parser.feed(': keep-alive\nevent: done\ndata: {"sessionId":"session-1"}')
  parser.flush()

  assert.deepEqual(events, [
    { event: 'done', data: '{"sessionId":"session-1"}' }
  ])
})

test('ignores frames without data and defaults unnamed events to message', () => {
  const events = []
  const parser = createSseParser(event => events.push(event))

  parser.feed('event: status\n\ndata: ready\n\n')

  assert.deepEqual(events, [{ event: 'message', data: 'ready' }])
})

test('posts chat data and emits parsed named events from a chunked response', async () => {
  const encoder = new TextEncoder()
  const events = []
  const chunks = [
    'event: metadata\ndata: {"sessionId":"session-',
    '1","routeType":"KNOWLEDGE_QA"}\n\nevent: delta\ndata: {"content":"你好"}\n\n',
    'event: done\ndata: {"sessionId":"session-1"}\n\n'
  ]
  const fetchImpl = async (url, options) => {
    assert.equal(url, '/api/chat/stream')
    assert.equal(options.method, 'POST')
    assert.equal(options.headers.Accept, 'text/event-stream')
    assert.deepEqual(JSON.parse(options.body), { sessionId: 'session-1', question: '你好' })
    return new Response(new ReadableStream({
      start(controller) {
        chunks.forEach(chunk => controller.enqueue(encoder.encode(chunk)))
        controller.close()
      }
    }), { status: 200, headers: { 'Content-Type': 'text/event-stream' } })
  }

  await postChatStream(
    { sessionId: 'session-1', question: '你好' },
    { fetchImpl, onEvent: event => events.push(event) }
  )

  assert.deepEqual(events, [
    { type: 'metadata', sessionId: 'session-1', routeType: 'KNOWLEDGE_QA' },
    { type: 'delta', content: '你好' },
    { type: 'done', sessionId: 'session-1' }
  ])
})

test('uses the response message when an HTTP request fails', async () => {
  const fetchImpl = async () => new Response(
    JSON.stringify({ code: 503, message: '模型服务暂不可用' }),
    { status: 503, headers: { 'Content-Type': 'application/json' } }
  )

  await assert.rejects(
    postChatStream({}, { fetchImpl, onEvent() {} }),
    error => error.message === '模型服务暂不可用' && error.code === 503
  )
})

test('rejects a successful response with a non-SSE content type', async () => {
  const fetchImpl = async () => new Response('{}', {
    status: 200,
    headers: { 'Content-Type': 'application/json' }
  })

  await assert.rejects(
    postChatStream({}, { fetchImpl, onEvent() {} }),
    error => error.message === '服务器未返回流式响应'
  )
})

test('decodes a multibyte character split across byte chunks', async () => {
  const bytes = new TextEncoder().encode(
    'event: delta\ndata: {"content":"你"}\n\n' +
    'event: done\ndata: {"sessionId":"session-1"}\n\n'
  )
  const events = []
  const fetchImpl = async () => new Response(new ReadableStream({
    start(controller) {
      for (const byte of bytes) controller.enqueue(Uint8Array.of(byte))
      controller.close()
    }
  }), { headers: { 'Content-Type': 'text/event-stream;charset=UTF-8' } })

  await postChatStream({}, { fetchImpl, onEvent: event => events.push(event) })

  assert.equal(events[0].content, '你')
})

test('delivers a server error event before rejecting the stream', async () => {
  const encoder = new TextEncoder()
  const events = []
  const fetchImpl = async () => new Response(new ReadableStream({
    start(controller) {
      controller.enqueue(encoder.encode(
        'event: delta\ndata: {"content":"部分回答"}\n\n' +
        'event: error\ndata: {"code":500,"message":"生成失败"}\n\n'
      ))
      controller.close()
    }
  }), { headers: { 'Content-Type': 'text/event-stream' } })

  await assert.rejects(
    postChatStream({}, { fetchImpl, onEvent: event => events.push(event) }),
    error => error.message === '生成失败' && error.code === 500
  )
  assert.deepEqual(events, [
    { type: 'delta', content: '部分回答' },
    { type: 'error', code: 500, message: '生成失败' }
  ])
})

test('rejects a response that closes before the done event', async () => {
  const encoder = new TextEncoder()
  const fetchImpl = async () => new Response(new ReadableStream({
    start(controller) {
      controller.enqueue(encoder.encode('event: delta\ndata: {"content":"半截回答"}\n\n'))
      controller.close()
    }
  }), { headers: { 'Content-Type': 'text/event-stream' } })

  await assert.rejects(
    postChatStream({}, { fetchImpl, onEvent() {} }),
    error => error.message === '流式响应意外中断'
  )
})

test('cancels the response reader when an SSE frame is invalid', async () => {
  const encoder = new TextEncoder()
  let cancelled = false
  const fetchImpl = async () => new Response(new ReadableStream({
    start(controller) {
      controller.enqueue(encoder.encode('event: delta\ndata: not-json\n\n'))
    },
    cancel() {
      cancelled = true
    }
  }), { headers: { 'Content-Type': 'text/event-stream' } })

  await assert.rejects(
    postChatStream({}, { fetchImpl, onEvent() {} }),
    error => error.message === '流式响应格式错误'
  )
  assert.equal(cancelled, true)
})

test('finishes at done without waiting for the server to close the socket', async () => {
  const encoder = new TextEncoder()
  let cancelled = false
  const fetchImpl = async () => new Response(new ReadableStream({
    start(controller) {
      controller.enqueue(encoder.encode(
        'event: done\ndata: {"sessionId":"session-1"}\n\n'
      ))
    },
    cancel() {
      cancelled = true
    }
  }), { headers: { 'Content-Type': 'text/event-stream' } })

  await postChatStream({}, { fetchImpl, onEvent() {} })

  assert.equal(cancelled, true)
})
