# 登录页个人签名透镜实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 用 HQC 的中英文职业签名替换登录页重复的 FundRAG 背景纹理，并在鼠标进入表单时隐藏透镜。

**架构：** `loginCopy.js` 将中英文背景纹理保存为三个签名单元组成的只读数组，`LoginScene.vue` 按网格下标循环渲染对应语言。深色透镜层复用浅色层的同一套主标题，并由主标题容器建立跟随响应式布局的纹理静区。表单继续通过事件向 `Login.vue` 报告透镜禁区，登录流程和指针坐标控制器保持不变。

**技术栈：** Vue 3、Node.js Test Runner、CSS `clip-path`、Vite

---

## 文件结构

- `smrag-web/src/constants/loginCopy.js`：保存中英文签名纹理数组。
- `smrag-web/src/components/LoginScene.vue`：循环渲染签名纹理并报告表单鼠标边界。
- `smrag-web/src/views/Login.vue`：组合透镜活动状态和表单阻止状态。
- `smrag-web/tests/loginScene.test.js`：验证签名内容、循环渲染、主标题与表单禁区契约。

### 任务 1：建立个人签名背景契约

**文件：**
- 修改：`smrag-web/tests/loginScene.test.js`

- [ ] **步骤 1：移除已放弃的固定简介断言**

删除对 `LOGIN_COPY.profile`、`scene-profile` 及左下定位样式的断言，保留表单透镜禁区测试。

- [ ] **步骤 2：编写失败的签名纹理测试**

增加以下断言：

```js
assert.deepEqual(LOGIN_COPY.zh.pattern, [
  'HQC',
  'AI + 全栈开发工程师',
  '2 年全栈经验'
])
assert.deepEqual(LOGIN_COPY.en.pattern, [
  'HQC',
  'AI + FULL-STACK ENGINEER',
  '2 YEARS FULL-STACK EXPERIENCE'
])
assert.match(sceneSource, /copy\.pattern\[item % copy\.pattern\.length\]/)
assert.match(sceneSource, /englishCopy\.pattern\[item % englishCopy\.pattern\.length\]/)
assert.doesNotMatch(sceneSource, /scene-profile/)
```

继续断言主标题：

```js
assert.equal(LOGIN_COPY.zh.title, '你好，我是 FundRAG')
assert.equal(LOGIN_COPY.en.title, "HELLO, I'M FUNDRAG")
```

- [ ] **步骤 3：运行测试验证失败**

运行：`node --test tests/loginScene.test.js`（工作目录：`smrag-web`）

预期：FAIL，旧 `pattern` 仍是单个 `FundRAG` 字符串。

### 任务 2：实现中英文签名纹理

**文件：**
- 修改：`smrag-web/src/constants/loginCopy.js`
- 修改：`smrag-web/src/components/LoginScene.vue`

- [ ] **步骤 1：将背景文案改为只读数组**

中文文案：

```js
pattern: Object.freeze([
  'HQC',
  'AI + 全栈开发工程师',
  '2 年全栈经验'
])
```

英文文案：

```js
pattern: Object.freeze([
  'HQC',
  'AI + FULL-STACK ENGINEER',
  '2 YEARS FULL-STACK EXPERIENCE'
])
```

删除已放弃的 `LOGIN_COPY.profile`。

- [ ] **步骤 2：按背景单元下标循环签名**

白色背景使用：

```vue
{{ copy.pattern[item % copy.pattern.length] }}
```

黑色内层使用：

```vue
{{ englishCopy.pattern[item % englishCopy.pattern.length] }}
```

删除 `scene-profile` 模板、脚本变量与全部定位样式。

- [ ] **步骤 3：调整不同长度签名的纹理排版**

背景网格文字使用有限宽度、居中和更紧凑的字距：

```css
.scene-pattern span {
  max-width: 12.5rem;
  text-align: center;
  line-height: 1.35;
}
```

`HQC` 与长职业文本共用相同纹理层级；白色页面保持低对比度，黑色透镜内使用浅灰白，并继续低于主标题的视觉层级。

- [ ] **步骤 4：运行定向测试**

运行：`node --test tests/loginScene.test.js`（工作目录：`smrag-web`）

预期：全部登录页测试 PASS。

### 任务 3：验证表单禁区和完整页面

**文件：**
- 修改：`smrag-web/src/components/LoginScene.vue`
- 修改：`smrag-web/src/views/Login.vue`
- 验证：`smrag-web/tests/loginScene.test.js`

- [ ] **步骤 1：确认表单只对鼠标阻止透镜**

表单保留：

```vue
@pointerenter="handleLensBlock($event, true)"
@pointerleave="handleLensBlock($event, false)"
```

`handleLensBlock` 使用 `isMousePointer()` 后发出 `lens-blocked`，`Login.vue` 使用 `lensActive && !lensBlocked`。

- [ ] **步骤 2：运行完整测试**

运行：`npm test`（工作目录：`smrag-web`）

预期：全部测试通过。

- [ ] **步骤 3：运行生产构建**

运行：`npm run build`（工作目录：`smrag-web`）

预期：退出码为 0；允许现有 `@vueuse/core` 第三方注释警告。

- [ ] **步骤 4：执行浏览器视觉检查**

桌面端确认默认背景显示中文签名、透镜显示英文签名、表单内透镜收起；窄屏确认透镜隐藏且无横向溢出。

- [ ] **步骤 5：检查补丁并压缩提交**

运行：

```bash
git diff --check
git reset --soft 752a780
git commit -m "feat: 添加登录页个人签名透镜"
```

预期：设计、计划、测试和实现最终合并为一个提交。

### 任务 4：收敛主标题与透镜叠层

**文件：**
- 修改：`smrag-web/tests/loginScene.test.js`
- 修改：`smrag-web/src/constants/loginCopy.js`
- 修改：`smrag-web/src/components/LoginScene.vue`

- [ ] **步骤 1：编写失败的叠层契约测试**

断言深色层不再引用英文主标题，而是第二次渲染同一套中文主标题；同时断言主标题容器存在覆盖纹理的伪元素静区。

```js
assert.equal(sceneSource.match(/\{\{ copy\.title \}\}/g)?.length, 2)
assert.doesNotMatch(sceneSource, /englishCopy\.(eyebrow|title|supporting)/)
assert.match(sceneSource, /\.scene-hero::before\s*\{[\s\S]*background:\s*var\(--scene-bg\)/)
assert.match(sceneSource, /\.scene-hero::before\s*\{[\s\S]*box-shadow:/)
```

- [ ] **步骤 2：运行定向测试确认失败**

运行：`node --test tests/loginScene.test.js`（工作目录：`smrag-web`）

预期：FAIL，当前深色层仍引用 `englishCopy.title`，且不存在主标题静区。

- [ ] **步骤 3：复用主标题并建立响应式静区**

深色层改用与浅色层一致的文案：

```vue
<section class="scene-hero">
  <p class="hero-eyebrow">{{ copy.eyebrow }}</p>
  <h1>{{ copy.title }}</h1>
  <p class="hero-supporting">{{ copy.supporting }}</p>
</section>
```

主标题容器使用匹配当前层背景色的伪元素遮住纹理：

```css
.scene-hero {
  isolation: isolate;
}

.scene-hero::before {
  content: '';
  position: absolute;
  inset: -2.5rem -3rem;
  z-index: -1;
  border-radius: 50%;
  background: var(--scene-bg);
  box-shadow: 0 0 2.5rem 2.5rem var(--scene-bg);
}
```

删除仅服务于英文主标题长度和字号的深色层覆盖样式，并删除不再使用的英文主标题常量。

- [ ] **步骤 4：验证定向测试与完整回归**

运行：

```bash
node --test tests/loginScene.test.js
npm test
npm run build
git diff --check
```

预期：全部测试通过；构建退出码为 0，仅允许现有 `@vueuse/core` 第三方注解警告。

- [ ] **步骤 5：执行浏览器视觉检查**

桌面端将透镜移过主标题，确认标题只发生黑白反色，静区中没有任何中英文签名；将透镜移到主标题之外，确认英文签名仍可见；进入表单确认透镜继续收起。窄屏确认透镜隐藏且无横向溢出。
