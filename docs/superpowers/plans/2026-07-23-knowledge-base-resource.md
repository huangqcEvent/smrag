# 知识库资源化实现计划

> **面向 AI 代理的工作者：** 在当前会话中使用 `superpowers-zh:executing-plans` 逐项实现，并使用复选框跟踪进度。

**目标：** 将硬编码的 `default` 替换为可查询、可展示、可校验的“企业知识库”资源，并让文档与 RAG 链路统一使用稳定 code。

**架构：** PostgreSQL 中以 `knowledge_base.code` 作为稳定主键，文档表通过外键引用。后端提供知识库列表与统一归一化服务；前端下拉选择器展示名称、提交 code。文档角色 ACL 保持独立且仍是检索和引用校验的权限依据。

**技术栈：** Spring Boot、MyBatis-Plus、Flyway、PostgreSQL/pgvector、Vue 3、Pinia、Element Plus、Node test runner。

---

### 任务 1：数据迁移与知识库领域模型

**文件：**

- 创建：`smrag-core/src/main/resources/db/migration/V9__knowledge_base_resource.sql`
- 创建：`smrag-core/src/main/java/com/hqc/smrag/entity/KnowledgeBase.java`
- 创建：`smrag-core/src/main/java/com/hqc/smrag/mapper/KnowledgeBaseMapper.java`
- 创建：`smrag-core/src/main/java/com/hqc/smrag/service/knowledge/KnowledgeBaseDefaults.java`
- 创建：`smrag-core/src/main/java/com/hqc/smrag/service/knowledge/KnowledgeBaseService.java`
- 创建：`smrag-core/src/main/java/com/hqc/smrag/vo/KnowledgeBaseVO.java`
- 测试：`smrag-core/src/test/java/com/hqc/smrag/service/knowledge/KnowledgeBaseServiceTest.java`

- [x] 先写测试：空值和 `default` 归一化为 `company-main`，停用或不存在的 code 被拒绝，列表只返回启用资源。
- [x] 运行 `mvn -pl smrag-core "-Dtest=KnowledgeBaseServiceTest" test`，确认因实现缺失而失败。
- [x] 创建迁移、实体、Mapper、默认值工具和服务。
- [x] 重跑目标测试，确认通过。

### 任务 2：知识库查询接口与文档链路

**文件：**

- 创建：`smrag-core/src/main/java/com/hqc/smrag/controller/KnowledgeBaseController.java`
- 修改：`smrag-core/src/main/java/com/hqc/smrag/controller/KnowledgeController.java`
- 修改：`smrag-core/src/main/java/com/hqc/smrag/service/knowledge/DocumentService.java`
- 测试：`smrag-core/src/test/java/com/hqc/smrag/controller/KnowledgeBaseControllerTest.java`
- 修改测试：`smrag-core/src/test/java/com/hqc/smrag/service/knowledge/DocumentServiceTest.java`
- 修改测试：`smrag-core/src/test/java/com/hqc/smrag/service/knowledge/DocumentMinioUploadTest.java`

- [x] 先写接口和文档默认 code 的失败测试。
- [x] 运行目标测试，确认预期失败。
- [x] 实现知识库列表接口，并让上传、列表、删除通过 `KnowledgeBaseService.requireActiveCode` 校验。
- [x] 重跑目标测试，确认通过。

### 任务 3：RAG 与聊天默认 code 统一

**文件：**

- 修改：`smrag-core/src/main/java/com/hqc/smrag/service/chat/AiChatService.java`
- 修改：`smrag-core/src/main/java/com/hqc/smrag/service/chat/KnowledgeQaService.java`
- 修改：`smrag-core/src/main/java/com/hqc/smrag/service/knowledge/VectorReindexRunner.java`
- 修改：`smrag-core/src/main/java/com/hqc/smrag/dto/DocumentParseTaskDTO.java`
- 修改测试：相关 `AiChatServiceTest`、`KnowledgeQaServiceTest`、`VectorReindexRunnerTest` 和 DTO 测试

- [x] 将默认知识库测试期望改为 `company-main` 并运行，确认失败。
- [x] 所有默认值统一调用 `KnowledgeBaseDefaults.normalizeCode`。
- [x] 重跑聊天、摄取和重建索引相关测试，确认通过。

### 任务 4：前端选择器与状态

**文件：**

- 创建：`smrag-web/src/constants/knowledgeBase.js`
- 修改：`smrag-web/src/api/knowledge.js`
- 修改：`smrag-web/src/stores/knowledge.js`
- 修改：`smrag-web/src/stores/chat.js`
- 修改：`smrag-web/src/views/Knowledge.vue`
- 修改：`smrag-web/src/views/Chat.vue`
- 创建：`smrag-web/tests/knowledgeBaseState.test.js`

- [x] 先写默认 code、旧值归一化和接口列表归一化的失败测试。
- [x] 运行 `npm test -- tests/knowledgeBaseState.test.js`，确认失败。
- [x] 增加知识库常量、列表 API 和 Pinia 状态。
- [x] 将两个页面的自由输入框替换为显示名称的 `el-select`，保留现有布局和响应式规则。
- [x] 重跑前端目标测试，确认通过。

### 任务 5：全量验证与提交

**文件：**

- 检查：所有本次修改文件

- [x] 运行 `mvn -pl smrag-core test`，确认全部测试通过。
- [x] 在 `smrag-web` 运行 `npm test`。
- [x] 在 `smrag-web` 运行 `npm run build`。
- [x] 启动 Flyway 或直接查询本地数据库，确认 `knowledge_base` 中存在 `company-main / 企业知识库`。
- [x] 检查 `git diff --check` 与 `git status`。
- [ ] 提交所有代码到当前 `master`。
