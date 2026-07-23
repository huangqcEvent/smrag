# RAG 角色 ID 权限过滤实现计划

> **面向 AI 代理的工作者：** 使用 superpowers:executing-plans 在当前 `master` 分支逐任务实现；这是用户明确指定的分支。步骤使用复选框跟踪进度。

**目标：** 让 RAG 使用 chunk 中的稳定角色 ID 权限元数据执行检索前强过滤，不再展开用户可访问的全部文档 ID。

**架构：** `doc_permission` 和 `sys_user_role` 仍是权限事实来源。索引侧将可读角色 ID 写成 `aclRoleId_<id>=true`，查询侧按用户当前角色 ID 构造 OR 过滤；引用与原文件仍通过关系数据库二次校验。

**技术栈：** Java 17、Spring Boot、Spring AI 1.1.2、MyBatis-Plus、PostgreSQL/PGVector、Flyway、JUnit 5、Mockito、Testcontainers。

---

## 文件结构

- `smrag-core/src/main/java/com/hqc/smrag/mapper/DocPermissionMapper.java`：查询文档可读角色 ID 和用户当前角色 ID。
- `smrag-core/src/main/java/com/hqc/smrag/mapper/VectorAclMapper.java`：清理旧角色键并原子更新向量 ACL 元数据。
- `smrag-core/src/main/java/com/hqc/smrag/service/DocPermissionService.java`：以角色 ID 生成权限快照，并提供用户当前角色 ID。
- `smrag-core/src/main/java/com/hqc/smrag/service/knowledge/DocumentChunker.java`：为新 chunk 写入角色 ID ACL。
- `smrag-core/src/main/java/com/hqc/smrag/service/knowledge/DocumentIngestionService.java`：解析时读取文档可读角色 ID。
- `smrag-core/src/main/java/com/hqc/smrag/service/knowledge/VectorReindexRunner.java`：重建时写入角色 ID ACL。
- `smrag-core/src/main/java/com/hqc/smrag/service/chat/AiChatService.java`：每次问答读取用户当前角色 ID，引用阶段仍读取可访问文档 ID。
- `smrag-core/src/main/java/com/hqc/smrag/service/chat/KnowledgeChatClient.java`：将检索权限参数语义改为角色 ID。
- `smrag-core/src/main/java/com/hqc/smrag/service/chat/KnowledgeQaService.java`：透传用户角色 ID。
- `smrag-core/src/main/java/com/hqc/smrag/service/chat/SpringAiKnowledgeChatClient.java`：构造角色 ID 强过滤表达式。
- `smrag-core/src/main/resources/db/migration/V8__use_role_ids_for_vector_acl.sql`：转换现有向量 ACL 元数据。
- 对应 `smrag-core/src/test/java` 测试：验证元数据、调用链、过滤表达式和迁移。

### 任务 1：角色 ID ACL 元数据

**文件：**

- 修改：`smrag-core/src/test/java/com/hqc/smrag/service/DocPermissionServiceTest.java`
- 修改：`smrag-core/src/test/java/com/hqc/smrag/service/knowledge/DocumentChunkerTest.java`
- 修改：`smrag-core/src/test/java/com/hqc/smrag/service/knowledge/DocumentIngestionServiceTest.java`
- 修改：`smrag-core/src/test/java/com/hqc/smrag/service/knowledge/VectorReindexRunnerTest.java`
- 修改：`smrag-core/src/main/java/com/hqc/smrag/mapper/DocPermissionMapper.java`
- 修改：`smrag-core/src/main/java/com/hqc/smrag/mapper/VectorAclMapper.java`
- 修改：`smrag-core/src/main/java/com/hqc/smrag/service/DocPermissionService.java`
- 修改：`smrag-core/src/main/java/com/hqc/smrag/service/knowledge/DocumentChunker.java`
- 修改：`smrag-core/src/main/java/com/hqc/smrag/service/knowledge/DocumentIngestionService.java`
- 修改：`smrag-core/src/main/java/com/hqc/smrag/service/knowledge/VectorReindexRunner.java`

- [ ] **步骤 1：编写失败测试**

  将断言从角色编码键改为稳定角色 ID 键：

  ```java
  assertThat(metadata)
      .containsEntry("aclRoleId_1", true)
      .containsEntry("aclRoleId_5", true)
      .doesNotContainKey("aclRole_ADMIN");
  ```

  `DocPermissionServiceTest` 同时验证更新 JSON 清除角色编码语义，解析与重建测试验证传递 `Set<Long>`。

- [ ] **步骤 2：运行测试验证失败**

  ```powershell
  mvn -pl smrag-core -Dtest=DocPermissionServiceTest,DocumentChunkerTest,DocumentIngestionServiceTest,VectorReindexRunnerTest test
  ```

  预期：FAIL，现有实现仍写入 `aclRole_<code>`，没有 `aclRoleId_<id>`。

- [ ] **步骤 3：实现最少生产代码**

  Mapper 新增：

  ```java
  List<Long> selectReadableRoleIdsByDocumentId(Long documentId);
  List<Long> selectRoleIdsByUserId(Long userId);
  ```

  元数据统一写入：

  ```java
  acl.put("aclVersion", aclVersion);
  acl.put("aclDenied", false);
  roleIds.forEach(id -> acl.put("aclRoleId_" + id, true));
  ```

  `VectorAclMapper` 在合并新 JSON 前同时删除 `aclRole_*` 与 `aclRoleId_*` 历史键。

- [ ] **步骤 4：运行定向测试验证通过**

  ```powershell
  mvn -pl smrag-core -Dtest=DocPermissionServiceTest,DocumentChunkerTest,DocumentIngestionServiceTest,VectorReindexRunnerTest test
  ```

  预期：全部通过。

- [ ] **步骤 5：提交任务**

  ```powershell
  git add smrag-core/src/main/java smrag-core/src/test/java
  git commit -m "refactor: store stable role ids in vector ACL"
  ```

### 任务 2：按用户当前角色执行 RAG 强过滤

**文件：**

- 修改：`smrag-core/src/test/java/com/hqc/smrag/service/chat/SpringAiKnowledgeChatClientTest.java`
- 修改：`smrag-core/src/test/java/com/hqc/smrag/service/chat/AiChatServiceTest.java`
- 修改：`smrag-core/src/test/java/com/hqc/smrag/service/chat/KnowledgeQaServiceTest.java`
- 修改：`smrag-core/src/main/java/com/hqc/smrag/service/chat/AiChatService.java`
- 修改：`smrag-core/src/main/java/com/hqc/smrag/service/chat/KnowledgeChatClient.java`
- 修改：`smrag-core/src/main/java/com/hqc/smrag/service/chat/KnowledgeQaService.java`
- 修改：`smrag-core/src/main/java/com/hqc/smrag/service/chat/SpringAiKnowledgeChatClient.java`

- [ ] **步骤 1：编写失败测试**

  过滤表达式测试递归读取 Spring AI `Filter.Expression`，验证：

  ```java
  assertThat(equalities)
      .containsEntry("aclRoleId_5", List.of(true))
      .containsEntry("aclRoleId_8", List.of(true));
  assertThat(equalities.get("documentId")).containsExactly(42L);
  ```

  `AiChatServiceTest` 验证检索前调用 `getUserRoleIds(7L)` 并把角色 ID 传入 `KnowledgeQaService`，引用返回前仍调用 `getAccessibleDocIds(7L)`。

- [ ] **步骤 2：运行测试验证失败**

  ```powershell
  mvn -pl smrag-core -Dtest=SpringAiKnowledgeChatClientTest,AiChatServiceTest,KnowledgeQaServiceTest test
  ```

  预期：FAIL，当前过滤仍把列表元素解释为 `documentId`。

- [ ] **步骤 3：实现最少生产代码**

  `AiChatService` 使用：

  ```java
  List<Long> roleIds = userId != null && docPermissionService != null
      ? docPermissionService.getUserRoleIds(userId)
      : null;
  ```

  `SpringAiKnowledgeChatClient` 对每个角色 ID生成：

  ```java
  filters.eq("aclRoleId_" + roleId, true)
  ```

  角色列表为空时保留不可能命中的 `documentId=-1`；指定文档时继续追加唯一的 `documentId` 条件。

- [ ] **步骤 4：运行定向测试验证通过**

  ```powershell
  mvn -pl smrag-core -Dtest=SpringAiKnowledgeChatClientTest,AiChatServiceTest,KnowledgeQaServiceTest test
  ```

  预期：全部通过。

- [ ] **步骤 5：提交任务**

  ```powershell
  git add smrag-core/src/main/java/com/hqc/smrag/service/chat smrag-core/src/test/java/com/hqc/smrag/service/chat
  git commit -m "fix: filter RAG retrieval by current role ids"
  ```

### 任务 3：迁移历史向量元数据

**文件：**

- 创建：`smrag-core/src/main/resources/db/migration/V8__use_role_ids_for_vector_acl.sql`
- 修改：`smrag-core/src/test/java/com/hqc/smrag/integration/RagSchemaMigrationTest.java`

- [ ] **步骤 1：编写失败的迁移测试**

  在 V7 基础库插入同时含旧 `aclRole_ADMIN` 的向量记录，升级后验证：

  ```sql
  metadata->>('aclRoleId_' || admin_role_id) = 'true'
  AND metadata->>'aclRole_ADMIN' IS NULL
  ```

  无可读角色文档应为 `aclDenied=true`。

- [ ] **步骤 2：运行测试验证失败**

  ```powershell
  mvn -pl smrag-core -Dtest=RagSchemaMigrationTest test
  ```

  预期：FAIL，尚不存在 V8 迁移，角色 ID 键未生成。

- [ ] **步骤 3：实现迁移**

  使用 `jsonb_object_agg('aclRoleId_' || dp.role_id, true)` 生成快照，更新前过滤所有匹配 `aclRole\_%` 和 `aclRoleId\_%` 的历史键，再合并 `aclVersion` 与 `aclDenied`。

- [ ] **步骤 4：运行迁移测试验证通过**

  ```powershell
  mvn -pl smrag-core -Dtest=RagSchemaMigrationTest test
  ```

  预期：Docker 可用时测试通过；Docker 不可用时测试明确跳过。

- [ ] **步骤 5：提交任务**

  ```powershell
  git add smrag-core/src/main/resources/db/migration/V8__use_role_ids_for_vector_acl.sql smrag-core/src/test/java/com/hqc/smrag/integration/RagSchemaMigrationTest.java
  git commit -m "fix: migrate vector ACL metadata to role ids"
  ```

### 任务 4：全量验证与数据库核对

**文件：**

- 检查：本计划涉及的全部代码与迁移文件。

- [ ] **步骤 1：运行格式和差异检查**

  ```powershell
  git diff --check HEAD~3
  rg -n "aclRole_" smrag-core/src/main smrag-core/src/test
  ```

  预期：无空白错误；`aclRole_` 只出现在 V8 清理旧数据和明确的“不应存在”测试中。

- [ ] **步骤 2：运行模块全量测试**

  ```powershell
  mvn -pl smrag-core test
  ```

  预期：0 个失败、0 个错误。

- [ ] **步骤 3：核对本地数据库**

  若本地 PostgreSQL 可用，执行只读查询确认最新文档的 chunk 均含 `aclRoleId_*`，不含 `aclRole_*`，并且 `aclVersion` 与 `knowledge_document` 一致。

- [ ] **步骤 4：检查工作区与提交**

  ```powershell
  git status --short
  git log -4 --oneline
  ```

  预期：只剩用户原有的未跟踪 `docs/技术文档.md`，本次实现均已提交。
