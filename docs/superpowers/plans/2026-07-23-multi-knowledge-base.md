# 多知识库与两层权限实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 用可管理的多知识库、知识库角色 ACL 和数据库强过滤 RAG 替换固定单知识库实现。

**架构：** PostgreSQL 使用数值知识库主键和关系型 ACL；文档 ACL 保持现有表结构。自定义 `AuthorizedPgVectorRetriever` 在向量 TopK 前同时关联知识库权限和文档权限。前端提供管理员知识库管理、文档迁移和显式 1–5 库问答选择。

**技术栈：** Spring Boot 3、MyBatis-Plus、Spring JDBC、Spring AI、PostgreSQL/pgvector、Flyway、Vue 3、Pinia、Element Plus、Node test runner。

---

## 文件结构

### 后端新增

- `entity/KnowledgeBaseRole.java`：知识库与现有角色的关系实体。
- `mapper/KnowledgeBaseRoleMapper.java`：知识库 ACL CRUD。
- `mapper/AuthorizedVectorMapper.java`：授权向量 SQL 查询。
- `mapper/projection/AuthorizedVectorRow.java`：授权向量查询投影。
- `dto/CreateKnowledgeBaseRequest.java`：创建参数。
- `dto/UpdateKnowledgeBaseRequest.java`：名称、说明和乐观锁版本。
- `dto/SetKnowledgeBaseRolesRequest.java`：整体替换角色集合。
- `dto/SetKnowledgeBaseStatusRequest.java`：启停参数。
- `dto/MoveDocumentRequest.java`：文档迁移目标。
- `vo/KnowledgeBaseAdminVO.java`：管理列表详情。
- `service/knowledge/PermissionDecisionService.java`：统一两层权限判定。
- `service/knowledge/DocumentTransferService.java`：文档迁移事务。
- `service/chat/AuthorizedPgVectorRetriever.java`：生成一次 embedding 并执行授权检索。
- `service/chat/KnowledgeRetrievalScope.java`：检索用户、知识库和文档范围。
- `controller/AdminKnowledgeBaseController.java`：管理 API。
- `controller/AdminDocumentController.java`：迁移 API。

### 后端修改

- `db/migration/V9__knowledge_base_resource.sql`：替换为最终空库 schema，不保留固定知识库。
- `entity/KnowledgeBase.java`、`KnowledgeDocument.java`：数值主键和数值外键。
- `service/knowledge/KnowledgeBaseService.java`：完整生命周期和用户可见列表。
- `service/RoleService.java`：删除角色时检查知识库 ACL。
- `service/DocPermissionService.java`、`controller/PermissionController.java`：校验文档角色是知识库角色子集。
- `service/knowledge/DocumentService.java`、`DocumentFileService.java`：必填知识库 code 和两层权限。
- `service/knowledge/DocumentChunker.java`、`DocumentIngestionService.java`、`VectorReindexRunner.java`：数值知识库 metadata。
- `dto/DocumentParseTaskDTO.java`：数值知识库 ID。
- `dto/ChatRequestDTO.java`：`knowledgeBaseCodes`，限制 1–5。
- `dto/CitationDTO.java`、`service/chat/CitationAssembler.java`：增加知识库名称。
- `service/chat/KnowledgeChatClient.java`、`SpringAiKnowledgeChatClient.java`、`KnowledgeQaService.java`、`AiChatService.java`：使用授权检索范围。

### 前端新增或修改

- `src/api/knowledgeBase.js`：管理和用户知识库 API。
- `src/constants/knowledgeBase.js`：移除固定知识库，保留 code 规范化和选择辅助。
- `src/stores/knowledge.js`：空初始状态、知识库 CRUD 和账号隔离。
- `src/stores/chat.js`：发送 `knowledgeBaseCodes`。
- `src/views/Knowledge.vue`：知识库导航、创建/设置、停用和迁移。
- `src/views/Chat.vue`：单库固定展示、多库最多选择 5 个。
- `tests/knowledgeBaseState.test.js`：空状态、多选限制和账号重置。

## 任务 1：重建最终数据库模型

**文件：**

- 修改：`smrag-core/src/main/resources/db/migration/V9__knowledge_base_resource.sql`
- 修改：`smrag-core/src/main/java/com/hqc/smrag/entity/KnowledgeBase.java`
- 修改：`smrag-core/src/main/java/com/hqc/smrag/entity/KnowledgeDocument.java`
- 创建：`smrag-core/src/main/java/com/hqc/smrag/entity/KnowledgeBaseRole.java`
- 创建：`smrag-core/src/main/java/com/hqc/smrag/mapper/KnowledgeBaseRoleMapper.java`
- 修改测试：`smrag-core/src/test/java/com/hqc/smrag/integration/RagSchemaMigrationTest.java`

- [ ] **步骤 1：编写失败的 schema 测试**

断言：

```java
assertColumnType(postgres, "knowledge_base", "id", "int8");
assertColumnType(postgres, "knowledge_document", "knowledge_base_id", "int8");
assertTableExists(postgres, "knowledge_base_role");
assertGeneratedColumnExists(postgres, "rag_vector_store", "knowledge_base_id");
assertThat(countRows(postgres, "knowledge_base")).isZero();
```

- [ ] **步骤 2：运行迁移测试确认失败**

运行：

```powershell
mvn -pl smrag-core -Dtest=RagSchemaMigrationTest test
```

预期：旧 V9 的 `knowledge_base.code` 仍是主键且文档外键是 `varchar`，断言失败。

- [ ] **步骤 3：替换 V9 并更新实体**

最终表关系：

```sql
CREATE TABLE knowledge_base (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(500),
    status VARCHAR(16) NOT NULL,
    version INT NOT NULL DEFAULT 1,
    created_by BIGINT NOT NULL REFERENCES sys_user(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE knowledge_base_role (
    knowledge_base_id BIGINT NOT NULL REFERENCES knowledge_base(id) ON DELETE CASCADE,
    role_id BIGINT NOT NULL REFERENCES sys_role(id),
    created_by BIGINT NOT NULL REFERENCES sys_user(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (knowledge_base_id, role_id)
);
```

清理知识表后把 `knowledge_document.knowledge_base_id` 重建为 `BIGINT NOT NULL`。为 `rag_vector_store` 增加从 metadata 生成的 `knowledge_base_id`、`document_id` 和 `document_acl_version`。

- [ ] **步骤 4：重跑迁移测试**

预期：schema 集成测试通过，数据库中没有初始知识库。

- [ ] **步骤 5：提交**

```bash
git add smrag-core/src/main/resources/db/migration/V9__knowledge_base_resource.sql smrag-core/src/main/java/com/hqc/smrag/entity smrag-core/src/main/java/com/hqc/smrag/mapper smrag-core/src/test/java/com/hqc/smrag/integration/RagSchemaMigrationTest.java
git commit -m "feat: rebuild multi knowledge base schema"
```

## 任务 2：实现知识库生命周期和现有角色兼容

**文件：**

- 创建：`smrag-core/src/main/java/com/hqc/smrag/dto/CreateKnowledgeBaseRequest.java`
- 创建：`smrag-core/src/main/java/com/hqc/smrag/dto/UpdateKnowledgeBaseRequest.java`
- 创建：`smrag-core/src/main/java/com/hqc/smrag/dto/SetKnowledgeBaseRolesRequest.java`
- 创建：`smrag-core/src/main/java/com/hqc/smrag/dto/SetKnowledgeBaseStatusRequest.java`
- 创建：`smrag-core/src/main/java/com/hqc/smrag/vo/KnowledgeBaseAdminVO.java`
- 修改：`smrag-core/src/main/java/com/hqc/smrag/service/knowledge/KnowledgeBaseService.java`
- 修改：`smrag-core/src/main/java/com/hqc/smrag/service/RoleService.java`
- 测试：`smrag-core/src/test/java/com/hqc/smrag/service/knowledge/KnowledgeBaseServiceTest.java`
- 修改测试：`smrag-core/src/test/java/com/hqc/smrag/service/RoleServiceTest.java`

- [ ] **步骤 1：编写生命周期失败测试**

覆盖：

```java
service.create(new CreateKnowledgeBaseRequest(
    "research", "投研知识库", "研究资料", List.of(2L)), 1L);
service.update(id, new UpdateKnowledgeBaseRequest("新名称", null, 1), 1L);
service.replaceRoles(id, new SetKnowledgeBaseRolesRequest(List.of(2L), 2), 1L);
service.changeStatus(id, new SetKnowledgeBaseStatusRequest("DISABLED", 3), 1L);
```

并断言重复 code、空角色、移除仍被文档使用的角色、非空删除和版本冲突均被拒绝。

- [ ] **步骤 2：运行目标测试确认失败**

```powershell
mvn -pl smrag-core -Dtest=KnowledgeBaseServiceTest,RoleServiceTest test
```

- [ ] **步骤 3：实现最少生命周期逻辑**

所有写操作使用事务。删除与角色替换通过 Mapper 的 `selectByIdForUpdate` 锁定知识库；`RoleService.delete` 增加 `knowledge_base_role` 引用计数。

- [ ] **步骤 4：重跑目标测试**

预期：知识库和角色测试全部通过。

- [ ] **步骤 5：提交**

```bash
git add smrag-core/src/main/java/com/hqc/smrag/dto smrag-core/src/main/java/com/hqc/smrag/vo smrag-core/src/main/java/com/hqc/smrag/service smrag-core/src/test
git commit -m "feat: add knowledge base lifecycle"
```

## 任务 3：统一两层权限和管理 API

**文件：**

- 创建：`smrag-core/src/main/java/com/hqc/smrag/service/knowledge/PermissionDecisionService.java`
- 创建：`smrag-core/src/main/java/com/hqc/smrag/controller/AdminKnowledgeBaseController.java`
- 修改：`smrag-core/src/main/java/com/hqc/smrag/controller/KnowledgeBaseController.java`
- 修改：`smrag-core/src/main/java/com/hqc/smrag/service/DocPermissionService.java`
- 修改：`smrag-core/src/main/java/com/hqc/smrag/controller/PermissionController.java`
- 测试：`smrag-core/src/test/java/com/hqc/smrag/service/knowledge/PermissionDecisionServiceTest.java`
- 测试：`smrag-core/src/test/java/com/hqc/smrag/controller/AdminKnowledgeBaseControllerTest.java`

- [ ] **步骤 1：编写权限失败测试**

```java
assertThat(permission.canAccessKnowledgeBase(7L, 12L)).isTrue();
assertThat(permission.canReadDocument(7L, 36L)).isFalse();
assertThatThrownBy(() -> permission.validateDocumentRoles(12L, List.of(999L)))
    .hasMessageContaining("不属于知识库");
```

同时覆盖 ADMIN 绕过、停用知识库、空权限和无权限用户列表。

- [ ] **步骤 2：运行测试确认失败**

```powershell
mvn -pl smrag-core -Dtest=PermissionDecisionServiceTest,AdminKnowledgeBaseControllerTest test
```

- [ ] **步骤 3：实现权限服务和 API**

普通列表使用当前 `userId` 查询启用且角色相交的知识库；管理控制器统一 `@PreAuthorize("hasRole('ADMIN')")`。文档权限保存前调用 `validateDocumentRoles`。

- [ ] **步骤 4：重跑权限和控制器测试**

预期：全部通过。

- [ ] **步骤 5：提交**

```bash
git add smrag-core/src/main/java/com/hqc/smrag/controller smrag-core/src/main/java/com/hqc/smrag/service smrag-core/src/test
git commit -m "feat: enforce knowledge base role access"
```

## 任务 4：改造文档链路并实现迁移

**文件：**

- 创建：`smrag-core/src/main/java/com/hqc/smrag/dto/MoveDocumentRequest.java`
- 创建：`smrag-core/src/main/java/com/hqc/smrag/service/knowledge/DocumentTransferService.java`
- 创建：`smrag-core/src/main/java/com/hqc/smrag/controller/AdminDocumentController.java`
- 修改：`smrag-core/src/main/java/com/hqc/smrag/controller/KnowledgeController.java`
- 修改：`smrag-core/src/main/java/com/hqc/smrag/service/knowledge/DocumentService.java`
- 修改：`smrag-core/src/main/java/com/hqc/smrag/service/knowledge/DocumentFileService.java`
- 修改：`smrag-core/src/main/java/com/hqc/smrag/dto/DocumentParseTaskDTO.java`
- 修改：`smrag-core/src/main/java/com/hqc/smrag/service/knowledge/DocumentChunker.java`
- 修改：`smrag-core/src/main/java/com/hqc/smrag/service/knowledge/VectorReindexRunner.java`
- 测试：`smrag-core/src/test/java/com/hqc/smrag/service/knowledge/DocumentTransferServiceTest.java`
- 修改测试：现有 DocumentService、DocumentFileService、DocumentChunker 和 ingestion 测试

- [ ] **步骤 1：编写数值知识库和迁移失败测试**

断言上传任务和 chunk metadata 的 `knowledgeBaseId` 是数值；迁移会拒绝 PROCESSING 文档和角色冲突，并在成功时更新文档及向量 metadata。

- [ ] **步骤 2：运行相关测试确认失败**

```powershell
mvn -pl smrag-core -Dtest=DocumentServiceTest,DocumentFileServiceTest,DocumentTransferServiceTest,DocumentChunkerTest,DocumentIngestionServiceTest test
```

- [ ] **步骤 3：实现文档链路**

上传和列表使用必填 `knowledgeBaseCode`。文件读取调用 `PermissionDecisionService.requireDocumentRead`。迁移 SQL：

```sql
UPDATE rag_vector_store
SET metadata = jsonb_set(metadata::jsonb, '{knowledgeBaseId}', to_jsonb(?::bigint), true)
WHERE document_id = ?;
```

同一事务更新 `knowledge_document.knowledge_base_id`。

- [ ] **步骤 4：重跑文档测试**

预期：全部通过。

- [ ] **步骤 5：提交**

```bash
git add smrag-core/src/main/java/com/hqc/smrag/controller smrag-core/src/main/java/com/hqc/smrag/dto smrag-core/src/main/java/com/hqc/smrag/service smrag-core/src/test
git commit -m "feat: scope documents to managed knowledge bases"
```

## 任务 5：实现授权 PGVector 检索和多库问答

**文件：**

- 创建：`smrag-core/src/main/java/com/hqc/smrag/mapper/projection/AuthorizedVectorRow.java`
- 创建：`smrag-core/src/main/java/com/hqc/smrag/mapper/AuthorizedVectorMapper.java`
- 创建：`smrag-core/src/main/java/com/hqc/smrag/service/chat/KnowledgeRetrievalScope.java`
- 创建：`smrag-core/src/main/java/com/hqc/smrag/service/chat/AuthorizedPgVectorRetriever.java`
- 修改：`smrag-core/src/main/java/com/hqc/smrag/dto/ChatRequestDTO.java`
- 修改：`smrag-core/src/main/java/com/hqc/smrag/dto/CitationDTO.java`
- 修改：`smrag-core/src/main/java/com/hqc/smrag/service/chat/CitationAssembler.java`
- 修改：`smrag-core/src/main/java/com/hqc/smrag/service/chat/KnowledgeChatClient.java`
- 修改：`smrag-core/src/main/java/com/hqc/smrag/service/chat/SpringAiKnowledgeChatClient.java`
- 修改：`smrag-core/src/main/java/com/hqc/smrag/service/chat/KnowledgeQaService.java`
- 修改：`smrag-core/src/main/java/com/hqc/smrag/service/chat/AiChatService.java`
- 测试：`smrag-core/src/test/java/com/hqc/smrag/service/chat/AuthorizedPgVectorRetrieverTest.java`
- 修改测试：ChatRequest、KnowledgeQaService、SpringAiKnowledgeChatClient、AiChatService 和 CitationAssembler 测试

- [ ] **步骤 1：编写多库检索失败测试**

覆盖：

- 0 个或超过 5 个 code 被拒绝。
- 任一知识库无权限时整个请求失败。
- embedding 只调用一次。
- 每个知识库独立请求 TopK，合并后按 score 去重。
- 空知识库 ACL 或空文档 ACL 返回零候选。
- Citation 包含知识库 ID 和名称。

- [ ] **步骤 2：运行目标测试确认失败**

```powershell
mvn -pl smrag-core -Dtest=AuthorizedPgVectorRetrieverTest,KnowledgeQaServiceTest,AiChatServiceTest,CitationAssemblerTest test
```

- [ ] **步骤 3：实现授权检索**

`AuthorizedPgVectorRetriever.retrieve(question, scope)`：

```java
float[] embedding = embeddingModel.embed(question);
return scope.knowledgeBaseIds().stream()
    .flatMap(id -> mapper.search(id, scope.userId(), scope.admin(),
        scope.documentId(), embedding, threshold, topK).stream())
    .collect(groupingBy(AuthorizedVectorRow::vectorId))
    .values().stream()
    .map(rows -> rows.get(0))
    .sorted(comparing(AuthorizedVectorRow::score).reversed())
    .limit(topK)
    .map(this::toDocument)
    .toList();
```

`SpringAiKnowledgeChatClient` 构建捕获 `KnowledgeRetrievalScope` 的 `DocumentRetriever`，继续复用 `RetrievalAugmentationAdvisor`。

- [ ] **步骤 4：运行目标与 PGVector 集成测试**

预期：授权过滤、多库合并和引用测试通过。

- [ ] **步骤 5：提交**

```bash
git add smrag-core/src/main/java/com/hqc/smrag/mapper smrag-core/src/main/java/com/hqc/smrag/service/chat smrag-core/src/main/java/com/hqc/smrag/dto smrag-core/src/test
git commit -m "feat: add authorized multi-base vector retrieval"
```

## 任务 6：实现前端多知识库管理和选择

**文件：**

- 创建：`smrag-web/src/api/knowledgeBase.js`
- 修改：`smrag-web/src/api/knowledge.js`
- 修改：`smrag-web/src/constants/knowledgeBase.js`
- 修改：`smrag-web/src/stores/knowledge.js`
- 修改：`smrag-web/src/stores/knowledgeAccountState.js`
- 修改：`smrag-web/src/stores/chat.js`
- 修改：`smrag-web/src/views/Knowledge.vue`
- 修改：`smrag-web/src/views/Chat.vue`
- 修改：`smrag-web/tests/knowledgeBaseState.test.js`

- [ ] **步骤 1：编写前端状态失败测试**

```js
assert.deepEqual(normalizeKnowledgeBases(null), [])
assert.deepEqual(limitKnowledgeBaseSelection(['a', 'b'], 5), ['a', 'b'])
assert.throws(() => validateKnowledgeBaseSelection([], 5))
assert.throws(() => validateKnowledgeBaseSelection(['a','b','c','d','e','f'], 5))
```

账号重置后知识库列表、当前管理知识库和聊天多选均为空。

- [ ] **步骤 2：运行测试确认失败**

```powershell
npm.cmd test -- tests/knowledgeBaseState.test.js
```

- [ ] **步骤 3：实现 API、Store 和页面**

知识库页面使用知识库导航和设置区域；聊天发送：

```js
{
  sessionId,
  question,
  knowledgeBaseCodes: selectedKnowledgeBaseCodes
}
```

一个可访问知识库时固定展示，多个时使用 `el-select multiple` 且 `multiple-limit="5"`。

- [ ] **步骤 4：运行前端测试和构建**

```powershell
npm.cmd test
npm.cmd run build
```

预期：全部测试和生产构建通过。

- [ ] **步骤 5：提交**

```bash
git add smrag-web
git commit -m "feat: add multi knowledge base workspace"
```

## 任务 7：清理本地数据并完成全量验收

**文件：**

- 检查：全部本次修改文件

- [ ] **步骤 1：清空限定知识数据**

PostgreSQL 清理知识相关表和旧 V9 记录，重新应用最终 V9；MinIO 只删除 `rag/document` 和 `rag/parsed-text` 前缀。保留用户、角色和聊天。

- [ ] **步骤 2：运行后端全量测试**

```powershell
mvn -pl smrag-core test
```

预期：0 failures、0 errors。

- [ ] **步骤 3：运行前端全量验证**

```powershell
cd smrag-web
npm.cmd test
npm.cmd run build
```

预期：测试和构建退出码均为 0。

- [ ] **步骤 4：真实启动和数据库核对**

启动后端，确认 Flyway 成功、Spring 上下文启动、知识库表为空、用户和角色仍存在。

- [ ] **步骤 5：检查和最终提交**

```bash
git diff --check
git status --short
git add -A
git commit -m "feat: deliver managed multi knowledge bases"
```
