import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { LOGIN_COPY } from '../src/constants/loginCopy.js'

const sceneSource = readFileSync(
  new URL('../src/components/LoginScene.vue', import.meta.url),
  'utf8'
)
const loginSource = readFileSync(
  new URL('../src/views/Login.vue', import.meta.url),
  'utf8'
)

test('defines matching Chinese and English login copy', () => {
  assert.equal(LOGIN_COPY.zh.title, '你好，我是私募 AI 专家')
  assert.equal(LOGIN_COPY.en.title, "HELLO, I'M SMRAG")
  assert.equal(LOGIN_COPY.zh.submit, '进入系统')
  assert.equal(LOGIN_COPY.en.submit, 'ENTER')
})

test('keeps the visual layer hidden and non-interactive', () => {
  assert.match(sceneSource, /:aria-hidden="interactive \? undefined : 'true'"/)
  assert.match(sceneSource, /:inert="!interactive"/)
  assert.match(sceneSource, /interactive \? 'form' : 'div'/)
  assert.match(sceneSource, /:tabindex="interactive \? 0 : -1"/)
})

test('exposes one event boundary for the interactive login form', () => {
  for (const eventName of [
    'update:username',
    'update:password',
    'toggle-password',
    'focus-field',
    'submit'
  ]) {
    assert.match(sceneSource, new RegExp(`'${eventName}'`))
  }
  assert.match(sceneSource, /@submit\.prevent="interactive && emit\('submit'\)"/)
})

test('renders one interactive Chinese scene and one English visual scene', () => {
  assert.equal((loginSource.match(/<LoginScene/g) || []).length, 2)
  assert.match(loginSource, /language="zh"/)
  assert.match(loginSource, /language="en"/)
  assert.match(loginSource, /:interactive="false"/)
  assert.match(loginSource, /createPointerLensController/)
})

test('keeps the existing safe login behavior', () => {
  assert.match(loginSource, /await authStore\.login/)
  assert.match(loginSource, /notifyError\(error, '登录失败'\)/)
  assert.match(loginSource, /route\.query\.redirect \|\| '\/chat'/)
})
