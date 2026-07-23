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
const appSource = readFileSync(
  new URL('../src/App.vue', import.meta.url),
  'utf8'
)
const routerSource = readFileSync(
  new URL('../src/router/index.js', import.meta.url),
  'utf8'
)
const indexSource = readFileSync(
  new URL('../index.html', import.meta.url),
  'utf8'
)

test('defines Chinese form copy and English hero reveal copy', () => {
  assert.equal(LOGIN_COPY.zh.brand, '基金智库')
  assert.equal(LOGIN_COPY.zh.title, '你好，我是基金智库')
  assert.equal(LOGIN_COPY.zh.pattern, '基金智库')
  assert.equal(LOGIN_COPY.zh.submit, '进入系统')
  assert.equal(LOGIN_COPY.en.title, "HELLO, I'M FUND INTELLIGENCE")
  assert.equal(LOGIN_COPY.en.pattern, 'FUND INTEL')
})

test('exposes one real login form event boundary', () => {
  for (const eventName of [
    'update:username',
    'update:password',
    'toggle-password',
    'focus-field',
    'submit'
  ]) {
    assert.match(sceneSource, new RegExp(`'${eventName}'`))
  }
  assert.match(sceneSource, /<form[\s\S]*@submit\.prevent="emit\('submit'\)"/)
  assert.doesNotMatch(sceneSource, /interactive \?/)
  assert.doesNotMatch(sceneSource, /:aria-hidden=/)
  assert.doesNotMatch(sceneSource, /:inert/)
})

test('renders one scene with a hero-only pointer reveal', () => {
  assert.equal((loginSource.match(/<LoginScene/g) || []).length, 1)
  assert.doesNotMatch(loginSource, /login-english-layer/)
  assert.match(loginSource, /pointerenter|pointermove|pointerleave/)
  assert.match(loginSource, /pointerLens|matchMedia/)
  assert.match(sceneSource, /class="login-hero-reveal"/)
  assert.match(loginSource, /:lens-active="lensActive"/)
  assert.match(sceneSource, /'is-active': lensActive/)
  assert.match(sceneSource, /\.login-hero-reveal\.is-active/)
  assert.doesNotMatch(sceneSource, /:global\(\.has-pointer-lens/)
  assert.match(sceneSource, /login-hero-reveal[\s\S]*englishCopy\.title/)
  assert.match(
    sceneSource,
    /class="login-hero-reveal"[\s\S]*<\/section>\s*<\/div>\s*<form/
  )
  assert.equal((sceneSource.match(/<form/g) || []).length, 1)
})

test('removes the top-left login brand group', () => {
  assert.doesNotMatch(sceneSource, /scene-brand/)
  assert.doesNotMatch(sceneSource, /brand-mark/)
  assert.doesNotMatch(sceneSource, /brand-name/)
})

test('places a larger login panel on the centered right side', () => {
  assert.match(sceneSource, /\.login-panel\s*\{[\s\S]*top:\s*50%/)
  assert.match(sceneSource, /\.login-panel\s*\{[\s\S]*right:\s*clamp\(/)
  assert.match(sceneSource, /\.login-panel\s*\{[\s\S]*width:\s*min\(26rem/)
  assert.match(sceneSource, /\.login-panel\s*\{[\s\S]*transform:\s*translateY\(-50%\)/)
  assert.match(sceneSource, /\.input-shell\s*\{[\s\S]*height:\s*3rem/)
  assert.match(sceneSource, /\.login-hero-reveal\s*\{[\s\S]*z-index:\s*2/)
  assert.match(sceneSource, /\.login-panel\s*\{[\s\S]*z-index:\s*3/)
})

test('keeps the existing safe login behavior', () => {
  assert.match(loginSource, /await authStore\.login/)
  assert.match(loginSource, /notifyError\(error, '登录失败'\)/)
  assert.match(loginSource, /route\.query\.redirect \|\| '\/chat'/)
})

test('lets the public login route use a full and scrollable viewport', () => {
  assert.match(appSource, /:class="\{ 'is-public-page': route\.meta\.public \}"/)
  assert.match(appSource, /v-if="!route\.meta\.public"/)
  assert.match(appSource, /\.app-shell\.is-public-page \.app-main\s*\{[\s\S]*overflow:\s*auto/)
})

test('uses the Fund Intelligence brand throughout the application shell', () => {
  assert.match(appSource, /aria-label="基金智库"/)
  assert.match(appSource, />基金智库</)
  assert.match(routerSource, /\| 基金智库/)
  assert.match(indexSource, /<title>基金智库<\/title>/)
})
