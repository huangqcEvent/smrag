# 账号隔离、文档可见性与动态角色实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:executing-plans 逐任务实现本计划。步骤使用复选框跟踪进度。

**目标：** 修复跨账号聊天状态与历史滚动，展示全部文档及角色权限，并提供受保护的动态角色管理。

**架构：** 前端认证状态负责触发领域 store 清理，聊天 store 提供幂等重置入口；后端文档目录一次查询全部元数据并批量附加授权角色与当前用户可访问标记；独立角色服务维护角色生命周期，ADMIN 作为受保护系统角色。所有内容访问仍由后端文档 ID 白名单控制。

**技术栈：** Spring Boot 3、Spring Security、MyBatis-Plus、JUnit 5、Mockito、Vue 3、Pinia、Element Plus、Node test、Vite。

---

## 文件结构

- 修改 `smrag-web/src/stores/chat.js`：提供完整账号级聊天状态重置。
- 修改 `smrag-web/src/stores/auth.js`、`smrag-web/src/App.vue`：统一登出和登录后的跨 store 协调。
- 修改 `smrag-web/src/views/Chat.vue`：修正历史加载后的滚动状态及 flex 高度链。
- 修改/创建 `smrag-web/tests/*`：覆盖账号清理和滚动状态助手。
- 修改 `smrag-core/src/main/java/com/hqc/smrag/vo/DocumentVO.java`：增加访问标记及授权角色。
- 修改 `smrag-core/src/main/java/com/hqc/smrag/mapper/DocPermissionMapper.java`、`service/DocPermissionService.java`、`controller/KnowledgeController.java`：批量组装文档权限。
- 创建 `RoleVO.java`、角色请求 DTO、`RoleService.java`、`RoleController.java`：动态角色 CRUD。
- 修改 `UserService.java`：角色删除引用校验所需行为保持一致。
- 修改 `smrag-web/src/api/user.js`、创建 `api/role.js`、修改 `views/Users.vue` 与 `views/Knowledge.vue`：动态角色和文档权限 UI。
- 创建对应后端服务/控制器测试，修改前端 Node 测试。

### 任务 1：账号级聊天状态清理

- [ ] 在前端 store 测试中构造“已有历史、会话列表、活动流”的状态，断言调用 `resetForAccountChange()` 后全部清空且 AbortController 被取消。
- [ ] 运行 `npm.cmd test`，确认测试因缺少重置 API 失败。
- [ ] 在 `chat.js` 实现幂等 `resetForAccountChange()`：停止生成、清空 `currentSessionId/chatHistory/sessionList/loading`，并导出该方法。
- [ ] 在 `auth.js` 的登录成功、手动登出、刷新失败路径触发统一账号切换清理；避免直接在 request 拦截器中形成 Pinia 循环依赖，改由 auth store 动态获取 chat store。
- [ ] 运行前端测试确认通过。

### 任务 2：历史会话滚动恢复

- [ ] 提取可测试的滚动状态函数，先写测试覆盖历史加载完成后强制贴底、用户向上滚动后流式内容不抢滚动位置。
- [ ] 运行前端测试确认红灯。
- [ ] 修改 `Chat.vue`：历史加载后 `nextTick` 再重置贴底状态与滚动位置；为 `.chat-page`、`.chat-main`、`.messages-container` 建立明确的 `min-height: 0` flex 高度链。
- [ ] 运行前端测试和 `npm.cmd run build`。

### 任务 3：文档目录返回访问状态和授权角色

- [ ] 新增 `KnowledgeControllerTest`/`DocPermissionServiceTest`：两个文档全部返回，其中一个 `accessible=false`；断言角色名称和编码批量映射正确。
- [ ] 运行目标测试确认当前过滤实现导致失败。
- [ ] 为 `DocumentVO` 增加 `accessible` 与 `allowedRoles`，创建轻量 `RoleVO`。
- [ ] 在 mapper 中增加按文档 ID 集合批量查询授权角色的联表查询结果，服务层按 documentId 分组。
- [ ] 修改 `KnowledgeController.list`：不再过滤文档，批量设置权限字段。
- [ ] 对未知/空角色授权保持空集合，管理员也根据真实角色权限显示 `accessible`。
- [ ] 运行目标测试确认通过。

### 任务 4：严格文档权限更新

- [ ] 新增服务测试：请求包含不存在角色时不删除旧授权并抛出业务异常；重复角色不会重复写入。
- [ ] 运行测试确认红灯。
- [ ] 修改 `DocPermissionService.setDocumentPermissions`：更新前一次性验证全部角色、去重，在事务内删除并重建，禁止静默跳过。
- [ ] 运行服务测试确认通过。

### 任务 5：动态角色后端

- [ ] 编写 `RoleServiceTest`，覆盖列表、新增、重复编码、更新名称描述、ADMIN 更新/删除保护、角色被用户引用时拒绝删除、安全删除。
- [ ] 运行测试确认缺少实现。
- [ ] 创建角色 DTO/VO、`RoleService` 与 `RoleController`，接口为 `GET/POST /api/roles`、`PUT/DELETE /api/roles/{id}`，全部要求 ADMIN。
- [ ] 角色编码规范化为大写字母、数字和下划线，长度 2 到 50；创建后不可修改。
- [ ] 删除前统计 `sys_user_role` 引用；ADMIN 永不允许删除；允许删除时依赖外键级联移除文档权限。
- [ ] 运行角色与安全相关测试。

### 任务 6：动态角色前端与文档权限展示

- [ ] 添加前端角色 API 单元测试，验证 CRUD 请求路径和负载。
- [ ] 创建 `api/role.js`，用户新增/编辑和文档上传/权限编辑都加载动态角色列表。
- [ ] 修改 `Users.vue`：增加角色管理区，ADMIN 显示“系统角色”且禁用删除；自定义角色支持新增、编辑、删除；用户角色文案显示真实名称。
- [ ] 修改 `Knowledge.vue`：每行展示授权角色标签及当前用户访问状态；无权限行显示锁图标和说明，禁止选择用于问答；管理员提供权限编辑入口。
- [ ] 避免嵌套卡片和仅靠颜色传递状态，锁图标、文字和禁用态共同表达权限。
- [ ] 运行前端测试和生产构建。

### 任务 7：完整回归验证

- [ ] 运行 `$env:JAVA_HOME='D:\JavaProject\development\jdk-21'; mvn -pl smrag-core test`，预期 0 failures、0 errors。
- [ ] 运行 `npm.cmd test`，预期全部通过。
- [ ] 运行 `npm.cmd run build`，预期退出码 0。
- [ ] 运行 `git diff --check`，预期无空白错误。
- [ ] 检查 `git status --short`，确认只包含本需求和用户原有未提交文件，无构建产物。
