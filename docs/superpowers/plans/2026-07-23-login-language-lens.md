# 登录页中英透镜实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 将登录页改造成默认中文、鼠标圆形区域显示英文的双层场景，同时保持唯一真实表单、移动端中文降级和现有认证行为。

**架构：** `Login.vue` 继续管理认证状态，并重叠渲染中文交互层与英文视觉层。`LoginScene.vue` 负责两层完全对齐的展示，英文层隐藏于无障碍树且不可交互；`pointerLens.js` 使用 `requestAnimationFrame` 合并指针更新并直接写入页面 CSS 变量。

**技术栈：** Vue 3 Composition API、CSS `clip-path`、Pointer Events、Node Test Runner、Vite。

---

## 文件结构

- 创建 `smrag-web/src/constants/loginCopy.js`：集中定义中英文登录页文案，保证双层结构使用一致字段。
- 创建 `smrag-web/src/utils/pointerLens.js`：提供设备能力判断和可测试的指针帧合并控制器。
- 创建 `smrag-web/src/components/LoginScene.vue`：渲染一个语言场景；仅中文实例包含可交互表单。
- 修改 `smrag-web/src/views/Login.vue`：保留登录业务，接入双层场景和透镜控制器。
- 创建 `smrag-web/tests/pointerLens.test.js`：验证设备降级、帧合并、进入和离开状态。
- 创建 `smrag-web/tests/loginScene.test.js`：验证文案、唯一交互层和无障碍边界。

### 任务 1：透镜状态控制

**文件：**
- 创建：`smrag-web/src/utils/pointerLens.js`
- 创建：`smrag-web/tests/pointerLens.test.js`

- [ ] **步骤 1：编写失败的设备能力和帧合并测试**

```js
import test from 'node:test'
import assert from 'node:assert/strict'
import {
  canUsePointerLens,
  createPointerLensController
} from '../src/utils/pointerLens.js'

test('only enables the lens for a fine hover pointer without reduced motion', () => {
  assert.equal(canUsePointerLens({
    canHover: true,
    hasFinePointer: true,
    reducedMotion: false
  }), true)
  assert.equal(canUsePointerLens({
    canHover: false,
    hasFinePointer: true,
    reducedMotion: false
  }), false)
  assert.equal(canUsePointerLens({
    canHover: true,
    hasFinePointer: true,
    reducedMotion: true
  }), false)
})

test('coalesces pointer moves and uses the latest coordinates', () => {
  let frame
  const positions = []
  const activeStates = []
  const controller = createPointerLensController({
    requestFrame: callback => {
      frame = callback
      return 1
    },
    cancelFrame() {},
    onPosition: (x, y) => positions.push([x, y]),
    onActive: active => activeStates.push(active)
  })

  controller.enter({ clientX: 10, clientY: 20 })
  controller.move({ clientX: 30, clientY: 40 })
  assert.deepEqual(positions, [])
  frame()
  assert.deepEqual(positions, [[30, 40]])
  controller.leave()
  assert.deepEqual(activeStates, [true, false])
})
```

- [ ] **步骤 2：运行测试验证失败**

运行：

```bash
cd smrag-web
node --test tests/pointerLens.test.js
```

预期：FAIL，提示无法找到 `src/utils/pointerLens.js`。

- [ ] **步骤 3：实现最小透镜控制器**

```js
export function canUsePointerLens({
  canHover,
  hasFinePointer,
  reducedMotion
}) {
  return canHover && hasFinePointer && !reducedMotion
}

export function createPointerLensController({
  requestFrame = callback => globalThis.requestAnimationFrame(callback),
  cancelFrame = frameId => globalThis.cancelAnimationFrame(frameId),
  onPosition,
  onActive
}) {
  let frameId = null
  let pendingPosition = null

  const flush = () => {
    frameId = null
    if (!pendingPosition) return
    const { x, y } = pendingPosition
    pendingPosition = null
    onPosition(x, y)
  }

  const schedule = event => {
    pendingPosition = { x: event.clientX, y: event.clientY }
    if (frameId == null) frameId = requestFrame(flush)
  }

  return {
    enter(event) {
      onActive(true)
      schedule(event)
    },
    move: schedule,
    leave() {
      onActive(false)
    },
    destroy() {
      if (frameId != null) cancelFrame(frameId)
      frameId = null
      pendingPosition = null
    }
  }
}
```

- [ ] **步骤 4：运行测试验证通过**

运行：`node --test tests/pointerLens.test.js`

预期：2 个测试全部 PASS。

- [ ] **步骤 5：提交任务 1**

```bash
git add smrag-web/src/utils/pointerLens.js smrag-web/tests/pointerLens.test.js
git commit -m "feat: 建立登录页指针透镜控制"
```

### 任务 2：双语场景展示组件

**文件：**
- 创建：`smrag-web/src/constants/loginCopy.js`
- 创建：`smrag-web/src/components/LoginScene.vue`
- 创建：`smrag-web/tests/loginScene.test.js`

- [ ] **步骤 1：编写失败的文案与结构测试**

```js
import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { LOGIN_COPY } from '../src/constants/loginCopy.js'

const sceneSource = readFileSync(
  new URL('../src/components/LoginScene.vue', import.meta.url),
  'utf8'
)

test('defines matching Chinese and English login copy', () => {
  assert.equal(LOGIN_COPY.zh.title, '你好，我是私募 AI 专家')
  assert.equal(LOGIN_COPY.en.title, "HELLO, I'M SMRAG")
  assert.equal(LOGIN_COPY.zh.submit, '进入系统')
  assert.equal(LOGIN_COPY.en.submit, 'ENTER')
})

test('keeps the visual layer hidden and non-interactive', () => {
  assert.match(sceneSource, /aria-hidden/)
  assert.match(sceneSource, /:inert="!interactive"/)
  assert.match(sceneSource, /interactive \? 'form' : 'div'/)
  assert.match(sceneSource, /tabindex/)
})
```

- [ ] **步骤 2：运行测试验证失败**

运行：`node --test tests/loginScene.test.js`

预期：FAIL，提示文案模块或组件不存在。

- [ ] **步骤 3：创建双语文案**

```js
export const LOGIN_COPY = Object.freeze({
  zh: Object.freeze({
    brand: '私募 AI 专家',
    eyebrow: '私募智能知识工作台',
    title: '你好，我是私募 AI 专家',
    supporting: '从企业知识中，找到可信、可追溯的答案。',
    account: '账号',
    accountPlaceholder: '请输入账号',
    password: '密码',
    passwordPlaceholder: '请输入密码',
    submit: '进入系统',
    loading: '正在进入'
  }),
  en: Object.freeze({
    brand: 'SMRAG',
    eyebrow: 'PRIVATE FUND INTELLIGENCE',
    title: "HELLO, I'M SMRAG",
    supporting: 'TRUSTED ANSWERS, GROUNDED IN YOUR KNOWLEDGE.',
    account: 'ACCOUNT',
    accountPlaceholder: 'YOUR ACCOUNT',
    password: 'PASSWORD',
    passwordPlaceholder: 'YOUR PASSWORD',
    submit: 'ENTER',
    loading: 'ENTERING'
  })
})
```

- [ ] **步骤 4：实现 `LoginScene.vue` 的单场景边界**

组件必须：

- 根节点设置 `login-scene--zh` 或 `login-scene--en`。
- 使用 30 个 `SMRAG` 文理单元作为低对比背景。
- 标题区和登录区在中英文实例中使用完全相同的布局。
- 登录区使用 `<component :is="interactive ? 'form' : 'div'">`。
- 非交互实例设置 `aria-hidden="true"`、`inert`、`pointer-events: none` 和输入 `tabindex="-1"`。
- 通过 `update:username`、`update:password`、`toggle-password`、`focus-field`、`submit` 事件把唯一交互层的动作交给 `Login.vue`。
- 通过 `activeField`、`loading`、`showPassword`、`username` 和 `password` 属性同步英文视觉层。

核心模板接口：

```vue
<script setup>
import { computed } from 'vue'
import { LOGIN_COPY } from '@/constants/loginCopy'

const props = defineProps({
  language: { type: String, required: true },
  interactive: { type: Boolean, default: false },
  username: { type: String, default: '' },
  password: { type: String, default: '' },
  showPassword: { type: Boolean, default: false },
  loading: { type: Boolean, default: false },
  activeField: { type: String, default: '' }
})
const emit = defineEmits([
  'update:username',
  'update:password',
  'toggle-password',
  'focus-field',
  'submit'
])
const copy = computed(() => LOGIN_COPY[props.language])
const patternItems = Array.from({ length: 30 }, (_, index) => index)
</script>
```

样式使用项目系统字体、暖白/深墨双色场景和青绿色交互色。登录区宽度为 `min(360px, calc(100vw - 48px))`，桌面端固定在右下侧；主标题位于中央偏左。输入、按钮和密码开关必须包含默认、悬停、聚焦、按下、禁用和加载状态。

- [ ] **步骤 5：运行场景测试和完整前端测试**

运行：

```bash
node --test tests/loginScene.test.js
npm test
```

预期：新增测试 PASS，现有测试无回退。

- [ ] **步骤 6：提交任务 2**

```bash
git add smrag-web/src/constants/loginCopy.js smrag-web/src/components/LoginScene.vue smrag-web/tests/loginScene.test.js
git commit -m "feat: 创建双语登录场景"
```

### 任务 3：登录页接入透镜和真实表单

**文件：**
- 修改：`smrag-web/src/views/Login.vue`
- 修改：`smrag-web/tests/loginScene.test.js`

- [ ] **步骤 1：增加失败的页面集成结构测试**

在 `loginScene.test.js` 中读取 `Login.vue` 并验证：

```js
const loginSource = readFileSync(
  new URL('../src/views/Login.vue', import.meta.url),
  'utf8'
)

test('renders one interactive Chinese scene and one English visual scene', () => {
  assert.equal((loginSource.match(/<LoginScene/g) || []).length, 2)
  assert.match(loginSource, /language="zh"/)
  assert.match(loginSource, /language="en"/)
  assert.match(loginSource, /:interactive="false"/)
  assert.match(loginSource, /createPointerLensController/)
})
```

- [ ] **步骤 2：运行测试验证失败**

运行：`node --test tests/loginScene.test.js`

预期：FAIL，旧 `Login.vue` 尚未使用 `LoginScene`。

- [ ] **步骤 3：替换登录页展示结构并保留业务逻辑**

`Login.vue` 保留现有 `handleLogin`，新增：

```js
import { onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import LoginScene from '@/components/LoginScene.vue'
import {
  canUsePointerLens,
  createPointerLensController
} from '@/utils/pointerLens'

const loginPage = ref(null)
const activeField = ref('')
let lensController = null

const setField = field => {
  activeField.value = field
}

onMounted(() => {
  const hover = window.matchMedia('(hover: hover)')
  const finePointer = window.matchMedia('(pointer: fine)')
  const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)')
  if (!canUsePointerLens({
    canHover: hover.matches,
    hasFinePointer: finePointer.matches,
    reducedMotion: reducedMotion.matches
  })) return

  lensController = createPointerLensController({
    onPosition(x, y) {
      loginPage.value?.style.setProperty('--lens-x', `${x}px`)
      loginPage.value?.style.setProperty('--lens-y', `${y}px`)
    },
    onActive(active) {
      loginPage.value?.classList.toggle('is-lens-active', active)
    }
  })
  loginPage.value?.classList.add('has-pointer-lens')
})

onBeforeUnmount(() => lensController?.destroy())
```

模板渲染：

```vue
<div
  ref="loginPage"
  class="login-page"
  @pointerenter="lensController?.enter($event)"
  @pointermove="lensController?.move($event)"
  @pointerleave="lensController?.leave()"
>
  <LoginScene
    language="zh"
    interactive
    :username="form.username"
    :password="form.password"
    :show-password="showPassword"
    :loading="loading"
    :active-field="activeField"
    @update:username="form.username = $event"
    @update:password="form.password = $event"
    @toggle-password="showPassword = !showPassword"
    @focus-field="setField"
    @submit="handleLogin"
  />
  <LoginScene
    class="login-english-layer"
    language="en"
    :interactive="false"
    :username="form.username"
    :password="form.password"
    :show-password="showPassword"
    :loading="loading"
    :active-field="activeField"
  />
</div>
```

页面样式要求：

```css
.login-page {
  --lens-x: 50vw;
  --lens-y: 50vh;
  --lens-radius: clamp(170px, 16vw, 230px);
  position: relative;
  min-height: 100vh;
  overflow: hidden;
  background: oklch(0.975 0.006 165);
}

.login-english-layer {
  z-index: 2;
  pointer-events: none;
  clip-path: circle(0 at var(--lens-x) var(--lens-y));
  transition: clip-path 180ms cubic-bezier(0.22, 1, 0.36, 1);
}

.has-pointer-lens.is-lens-active .login-english-layer {
  clip-path: circle(var(--lens-radius) at var(--lens-x) var(--lens-y));
}

@media (hover: none), (pointer: coarse), (max-width: 720px) {
  .login-english-layer { display: none; }
}

@media (prefers-reduced-motion: reduce) {
  .login-english-layer { display: none; }
}
```

删除原渐变背景、浮动圆形和玻璃登录卡片样式。

- [ ] **步骤 4：运行页面测试和完整测试**

运行：

```bash
node --test tests/loginScene.test.js tests/pointerLens.test.js
npm test
```

预期：所有测试 PASS。

- [ ] **步骤 5：提交任务 3**

```bash
git add smrag-web/src/views/Login.vue smrag-web/tests/loginScene.test.js
git commit -m "feat: 接入登录页中英透镜"
```

### 任务 4：视觉验证与交付

**文件：**
- 修改：仅限视觉验证发现的 `Login.vue`、`LoginScene.vue` 或相关测试。

- [ ] **步骤 1：启动本地前端并检查桌面交互**

运行：`npm run dev`

在浏览器打开 `http://localhost:3000/login`，验证：

- 中文层默认完整可见。
- 圆形区域跟随鼠标并显示英文层。
- 圆形经过标题、输入框和按钮时双层位置完全对齐。
- 中文表单仍能点击、输入、显示密码并回车提交。
- 输入内容、焦点、密码显示和加载视觉同步到英文层。

- [ ] **步骤 2：检查响应式和无障碍降级**

检查 390×844 移动视口和减少动态效果环境：

- 英文层不显示。
- 主标题不遮挡表单。
- 登录区底部居中且无需横向滚动。
- Tab 顺序只经过用户名、密码、密码开关和登录按钮一次。

- [ ] **步骤 3：运行最终验证**

运行：

```bash
npm test
npm run build
git diff --check
```

预期：测试全部 PASS，Vite 构建退出码为 0，Git 无空白错误。

- [ ] **步骤 4：提交视觉修正**

若视觉检查产生修改：

```bash
git add smrag-web/src/views/Login.vue smrag-web/src/components/LoginScene.vue smrag-web/tests
git commit -m "fix: 完善登录页透镜适配"
```

若没有修改，不创建空提交。
