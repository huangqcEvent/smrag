# FundRAG 品牌替换实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 将所有用户可见的“基金智库”品牌统一为“FundRAG”，并保持登录页现有透镜与表单交互不变。

**架构：** 登录页品牌文案继续由 `loginCopy.js` 单点提供，应用外壳、路由标题与 HTML 回退标题分别更新其静态品牌文本。先修改品牌契约测试并确认失败，再进行最小文案替换，最后扫描旧名称并运行完整前端验证。

**技术栈：** Vue 3、Vue Router、Node.js Test Runner、Vite

---

## 文件结构

- `smrag-web/tests/loginScene.test.js`：定义登录页和应用外壳的品牌一致性契约。
- `smrag-web/src/constants/loginCopy.js`：集中提供登录页中英文品牌文案。
- `smrag-web/src/App.vue`：显示导航栏品牌名及无障碍标签。
- `smrag-web/src/router/index.js`：生成浏览器页面标题。
- `smrag-web/index.html`：提供加载阶段的默认页面标题。
- `README.md`、`smrag-web/README.md`、`PRODUCT.md`：统一项目说明中的产品名称。
- `docs/superpowers/specs/2026-07-23-login-hero-lens-design.md`、`docs/superpowers/plans/2026-07-23-login-hero-lens.md`：同步已生效登录设计文档中的品牌名称。

### 任务 1：更新品牌契约

**文件：**
- 修改：`smrag-web/tests/loginScene.test.js`

- [ ] **步骤 1：编写失败的品牌测试**

将品牌断言改为：

```js
assert.equal(LOGIN_COPY.zh.brand, 'FundRAG')
assert.equal(LOGIN_COPY.zh.title, '你好，我是 FundRAG')
assert.equal(LOGIN_COPY.zh.pattern, 'FundRAG')
assert.equal(LOGIN_COPY.en.title, "HELLO, I'M FUNDRAG")
assert.equal(LOGIN_COPY.en.pattern, 'FUNDRAG')
assert.match(appSource, /aria-label="FundRAG"/)
assert.match(appSource, />FundRAG</)
assert.match(routerSource, /\| FundRAG/)
assert.match(indexSource, /<title>FundRAG<\/title>/)
```

- [ ] **步骤 2：运行测试验证失败**

运行：`npm test -- --test-name-pattern="brand"`（工作目录：`smrag-web`）

预期：FAIL，实际值仍包含“基金智库”。

### 任务 2：实施全站文案替换

**文件：**
- 修改：`smrag-web/src/constants/loginCopy.js`
- 修改：`smrag-web/src/App.vue`
- 修改：`smrag-web/src/router/index.js`
- 修改：`smrag-web/index.html`
- 修改：`README.md`
- 修改：`smrag-web/README.md`
- 修改：`PRODUCT.md`
- 修改：`docs/superpowers/specs/2026-07-23-login-hero-lens-design.md`
- 修改：`docs/superpowers/plans/2026-07-23-login-hero-lens.md`

- [ ] **步骤 1：替换登录页集中品牌文案**

`loginCopy.js` 使用：

```js
pattern: 'FundRAG',
brand: 'FundRAG',
title: '你好，我是 FundRAG',
panelKicker: 'FundRAG / 访问入口',
```

英文透镜使用：

```js
pattern: 'FUNDRAG',
title: "HELLO, I'M FUNDRAG",
```

- [ ] **步骤 2：替换应用外壳与页面标题**

将 `App.vue` 的品牌文本和 `aria-label`、`router/index.js` 的标题后缀、`index.html` 的 `<title>` 全部改为 `FundRAG`。

- [ ] **步骤 3：同步项目说明和登录设计文档**

将本计划文件结构所列文档中的产品旧名称替换为 `FundRAG`，不修改历史技术类名 `FundTools`。

- [ ] **步骤 4：运行品牌测试验证通过**

运行：`npm test -- --test-name-pattern="brand"`（工作目录：`smrag-web`）

预期：品牌相关测试 PASS。

### 任务 3：完成回归验证与提交

**文件：**
- 验证：上述全部修改文件

- [ ] **步骤 1：扫描旧品牌残留**

运行：`rg -n "基金智库" README.md PRODUCT.md smrag-web docs/superpowers/specs/2026-07-23-login-hero-lens-design.md docs/superpowers/plans/2026-07-23-login-hero-lens.md`

预期：仅 FundRAG 命名规格中用于描述迁移目标的旧名称允许保留。

- [ ] **步骤 2：运行完整测试**

运行：`npm test`（工作目录：`smrag-web`）

预期：全部测试通过。

- [ ] **步骤 3：运行生产构建**

运行：`npm run build`（工作目录：`smrag-web`）

预期：退出码为 0；允许现有第三方依赖的注释警告。

- [ ] **步骤 4：检查补丁格式**

运行：`git diff --check`

预期：无输出且退出码为 0。

- [ ] **步骤 5：提交**

```bash
git add README.md PRODUCT.md smrag-web docs/superpowers
git commit -m "feat: 品牌升级为 FundRAG"
```
