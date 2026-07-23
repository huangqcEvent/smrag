# 前端安全错误反馈实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 消除前端对“系统繁忙，请稍后重试”、Axios 原始错误和其他技术信息的直接展示，并保证一次失败只弹一次。

**架构：** 用纯函数模块统一分类和安全化错误文案，用通知模块集中调用 Element Plus 并按错误对象去重。Axios、SSE 和页面操作全部复用这两个边界，页面只提供操作级兜底文案。

**技术栈：** Vue 3、Axios、Element Plus、Node Test Runner

---

## 文件结构

- 创建 `smrag-web/src/utils/safeError.js`：纯函数错误分类、技术文案过滤和安全文案生成。
- 创建 `smrag-web/src/utils/errorFeedback.js`：Element Plus 错误通知和同一错误对象去重。
- 创建 `smrag-web/src/utils/sessionExpiry.js`：统一 Axios 与 Fetch/SSE 的
  401 会话失效处理，并校验请求 Token，避免旧账号的迟到响应退出新账号。
- 创建 `smrag-web/tests/errorFeedback.test.js`：安全文案和通知去重回归测试。
- 修改 `smrag-web/src/utils/request.js`：Axios 成功包络和失败响应统一走安全通知。
- 修改 `smrag-web/src/api/*.js`：为每个请求提供操作级兜底，登录接口显式信任凭据错误文案。
- 修改 `smrag-web/src/api/chat.js`、`smrag-web/src/stores/chatStreamState.js`：SSE 弹窗、401 和内联错误统一安全化。
- 修改 `smrag-web/src/views/Login.vue`、`Users.vue`、`Knowledge.vue`：页面级错误改用统一通知并传递操作级兜底。

### 任务 1：建立安全错误策略

**文件：**
- 创建：`smrag-web/tests/errorFeedback.test.js`
- 创建：`smrag-web/src/utils/safeError.js`

- [ ] **步骤 1：编写失败的文案策略测试**

测试必须断言：

```js
assert.equal(toUserErrorMessage({
  response: { status: 500, data: { message: '系统繁忙，请稍后重试' } },
  message: 'Request failed with status code 500'
}, '保存失败'), '保存失败')

assert.equal(toUserErrorMessage({
  response: { status: 403, data: { message: '没有文档权限' } }
}), '没有文档权限')

assert.equal(toUserErrorMessage({
  code: 'ECONNABORTED',
  message: 'timeout of 30000ms exceeded'
}), '请求超时，请检查网络连接')
```

- [ ] **步骤 2：运行测试并确认因模块不存在而失败**

运行：`npm.cmd test -- tests/errorFeedback.test.js`

预期：FAIL，提示无法导入 `src/utils/safeError.js`。

- [ ] **步骤 3：实现最小错误分类函数**

实现并导出：

```js
export function isSilentError(error) {}
export function toUserErrorMessage(error, fallback = '当前操作未完成') {}
```

规则严格遵循设计规格：取消静默、401 固定提示、安全业务文案保留、超时和网络分类、5xx 使用操作级兜底、技术模式永不透传。

- [ ] **步骤 4：运行测试确认策略通过**

运行：`npm.cmd test -- tests/errorFeedback.test.js`

预期：新增策略测试全部 PASS。

### 任务 2：建立单次通知边界并接入 Axios

**文件：**
- 修改：`smrag-web/tests/errorFeedback.test.js`
- 创建：`smrag-web/src/utils/errorFeedback.js`
- 修改：`smrag-web/src/utils/request.js`

- [ ] **步骤 1：编写失败的通知去重测试**

使用注入的通知函数验证同一错误对象调用两次只记录一次，`AbortError` 不记录：

```js
const messages = []
const notify = createErrorNotifier(message => messages.push(message))
const error = new Error('Request failed with status code 500')
notify(error, '保存失败')
notify(error, '保存失败')
notify(new DOMException('aborted', 'AbortError'), '保存失败')
assert.deepEqual(messages, ['保存失败'])
```

- [ ] **步骤 2：运行测试确认缺少通知实现**

运行：`npm.cmd test -- tests/errorFeedback.test.js`

预期：FAIL，提示 `createErrorNotifier` 未定义或模块不存在。

- [ ] **步骤 3：实现通知器并替换 Axios 直接弹窗**

`errorFeedback.js` 导出可测试的 `createErrorNotifier(showError)` 和使用 `ElMessage.error` 的共享 `notifyError`。`request.js` 的业务包络错误和响应错误只调用 `notifyError`；401 保持退出和跳转，但提示也通过同一入口。

- [ ] **步骤 4：运行策略测试**

运行：`npm.cmd test -- tests/errorFeedback.test.js`

预期：全部 PASS，重复通知计数为 1。

### 任务 3：接入流式聊天和页面操作

**文件：**
- 修改：`smrag-web/src/api/chat.js`
- 修改：`smrag-web/src/stores/chatStreamState.js`
- 修改：`smrag-web/src/views/Login.vue`
- 修改：`smrag-web/src/views/Users.vue`
- 修改：`smrag-web/src/views/Knowledge.vue`
- 修改：`smrag-web/tests/chatStreamState.test.js`

- [ ] **步骤 1：为 SSE 内联错误编写失败测试**

新增事件：

```js
applyChatStreamEvent(message, {
  type: 'error',
  code: 500,
  message: '系统繁忙，请稍后重试'
})
assert.equal(message.error, '生成回答失败')
```

- [ ] **步骤 2：运行测试确认原始文案仍会泄漏**

运行：`npm.cmd test -- tests/chatStreamState.test.js`

预期：FAIL，实际值仍为“系统繁忙，请稍后重试”。

- [ ] **步骤 3：统一所有直接错误弹窗**

流式聊天使用 `notifyError(error, '生成回答失败')`；聊天状态使用 `toUserErrorMessage(event, '生成回答失败')`。登录、用户管理和知识库页面把所有 `ElMessage.error(error.message || fallback)` 及重复静态错误弹窗替换为 `notifyError(error, fallback)`，确认框取消分支保持静默。

- [ ] **步骤 4：运行相关测试**

运行：`npm.cmd test -- tests/errorFeedback.test.js tests/chatStreamState.test.js`

预期：全部 PASS。

### 任务 4：全量验证与提交

**文件：**
- 修改：以上全部实现与测试文件

- [ ] **步骤 1：搜索禁止文案和直接错误透传**

运行：

```powershell
rg -n "系统繁忙|Request failed with status code|ElMessage\.error\(error.*message" smrag-web/src
```

预期：无可直接展示禁止文案的生产代码；测试模式常量除外。

- [ ] **步骤 2：运行前端全量测试**

运行：`npm.cmd test`

预期：全部 PASS，0 failures。

- [ ] **步骤 3：运行生产构建**

运行：`npm.cmd run build`

预期：退出码 0。

- [ ] **步骤 4：检查并提交**

运行：

```powershell
git diff --check
git add --all
git diff --cached --check
git commit -m "fix: 统一前端安全错误提示"
```

预期：提交成功，工作区干净。
