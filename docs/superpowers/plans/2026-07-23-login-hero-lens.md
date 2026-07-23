# 基金智库标题透镜登录页实现计划

> **面向 AI 代理的工作者：** 使用 superpowers:executing-plans 在当前会话逐项实现，所有行为变更遵循红、绿、重构循环。

**目标：** 将透镜限制在标题与背景区域，保证放大的右侧中文登录面板完全不参与变色或换字。

**架构：** `Login.vue` 管理一套场景和透镜指针状态；`LoginScene.vue` 将中文基础标题、英文标题透镜和唯一中文表单分层；`pointerLens.js` 处理设备能力与坐标更新。

**技术栈：** Vue 3、CSS `clip-path`、Pointer Events、Node.js test runner、Vite。

---

### 任务 1：用测试定义标题专用透镜

**文件：**
- 修改：`smrag-web/tests/loginScene.test.js`
- 修改：`smrag-web/tests/pointerLens.test.js`

- [x] 断言页面只有一个场景和一套真实表单。
- [x] 断言英文透镜层在表单之前结束，不包含表单副本。
- [x] 断言鼠标可触发，触摸、手写笔和空指针类型不能触发。
- [x] 断言无左上角品牌标识，表单右侧垂直居中且尺寸放大。
- [x] 运行目标测试，确认测试因当前实现缺少标题专用透镜而失败。

### 任务 2：实现标题透镜与静态表单

**文件：**
- 修改：`smrag-web/src/views/Login.vue`
- 修改：`smrag-web/src/components/LoginScene.vue`
- 修改：`smrag-web/src/constants/loginCopy.js`
- 修改：`smrag-web/src/utils/pointerLens.js`

- [x] `Login.vue` 只渲染一个 `LoginScene`，恢复安全的鼠标透镜控制。
- [x] `LoginScene.vue` 添加仅包含英文纹理和标题的遮罩层。
- [x] 唯一中文表单放在遮罩层上方，始终保持白色中文内容。
- [x] 文案常量保留中文完整文案与英文标题文案。
- [x] 运行目标测试，确认 12 项全部通过。

### 任务 3：调整表单与响应式布局

**文件：**
- 修改：`smrag-web/src/components/LoginScene.vue`
- 修改：`smrag-web/src/views/Login.vue`
- 修改：`smrag-web/src/App.vue`

- [x] 删除左上角品牌标识。
- [x] 面板宽度调整为 `26rem`，增加内边距并将输入框高度调整为 `3rem`。
- [x] 桌面端使用右侧垂直居中定位。
- [x] 窄屏采用上下布局并关闭透镜，小高度环境允许纵向滚动。

### 任务 4：浏览器与生产验证

**文件：**
- 验证：`smrag-web/src/views/Login.vue`
- 验证：`smrag-web/src/components/LoginScene.vue`

- [x] 运行 `npm test`，全部 53 项通过。
- [x] 运行 `npm run build`，退出码为 0。
- [x] 桌面检查标题透镜正常，表单颜色和中文文案不变化。
- [x] 窄屏和小高度检查无横向溢出且内容可滚动。
- [x] 最终代码审查无 Critical 和 Important 问题。
- [ ] 提交实现代码。
