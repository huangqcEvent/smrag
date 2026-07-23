# 基金智库静态登录页实现计划

> **面向 AI 代理的工作者：** 使用 superpowers:executing-plans 在当前会话逐项实现，所有行为变更遵循红、绿、重构循环。

**目标：** 删除登录页透镜和英文副本，将放大的登录面板放在右侧垂直居中，同时保留现有认证行为和响应式可用性。

**架构：** `Login.vue` 只负责一套中文场景和认证状态；`LoginScene.vue` 渲染唯一表单；`loginCopy.js` 仅保存实际使用的中文文案。移除指针透镜工具和测试，使用登录场景测试约束静态结构与布局。

**技术栈：** Vue 3、CSS、Node.js test runner、Vite。

---

### 任务 1：用测试定义静态中文页面

**文件：**
- 修改：`smrag-web/tests/loginScene.test.js`
- 删除：`smrag-web/tests/pointerLens.test.js`

- [ ] 将双层中英文断言改为单个中文场景断言。
- [ ] 断言 `Login.vue` 不含英文层、指针事件或透镜工具引用。
- [ ] 断言 `LoginScene.vue` 不含左上角品牌标识。
- [ ] 断言登录面板使用右侧、垂直居中和放大宽度。
- [ ] 运行 `node --test tests/loginScene.test.js`，确认测试因当前双层实现而失败。

### 任务 2：删除透镜与英文副本

**文件：**
- 修改：`smrag-web/src/views/Login.vue`
- 修改：`smrag-web/src/components/LoginScene.vue`
- 修改：`smrag-web/src/constants/loginCopy.js`
- 删除：`smrag-web/src/utils/pointerLens.js`

- [ ] `Login.vue` 只渲染一个可交互的 `LoginScene`。
- [ ] 删除指针监听、媒体查询、遮罩样式和英文视觉层。
- [ ] 将 `LoginScene.vue` 收敛为固定中文真实表单，删除语言和视觉副本分支。
- [ ] 从文案常量中删除英文数据及透镜专用字段。
- [ ] 运行目标测试，确认静态结构断言通过。

### 任务 3：调整表单布局与响应式

**文件：**
- 修改：`smrag-web/src/components/LoginScene.vue`
- 修改：`smrag-web/src/App.vue`
- 测试：`smrag-web/tests/loginScene.test.js`

- [ ] 删除左上角品牌标识的模板和样式。
- [ ] 将面板宽度调整为约 `26rem`，增加内边距与输入框高度。
- [ ] 桌面端使用右侧垂直居中定位。
- [ ] 中小屏和窄屏避免标题、表单重叠，并允许小高度窗口纵向滚动。
- [ ] 运行目标测试，确认布局约束通过。

### 任务 4：浏览器与生产验证

**文件：**
- 验证：`smrag-web/src/views/Login.vue`
- 验证：`smrag-web/src/components/LoginScene.vue`

- [ ] 运行 `npm test`，预期全部通过。
- [ ] 运行 `npm run build`，预期退出码为 0。
- [ ] 在桌面视口检查静态中文、无 Logo、右侧居中大表单。
- [ ] 在窄屏和小高度视口检查无横向溢出、内容可滚动。
- [ ] 请求最终代码审查，修复所有 Critical 和 Important 问题。
- [ ] 提交实现代码。
