# MinIO 文档预览与 RAG ACL 实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers-zh:executing-plans 逐任务实现本计划。每个生产改动严格遵循 RED → GREEN → REFACTOR。

**目标：** 将新文档原文件和解析文本存入私有 MinIO，提供鉴权后的 PDF/TXT 在线阅读及 Word 下载，并在 chunk metadata、向量检索、模型上下文、引用返回和文件读取阶段执行一致的角色权限校验。

**架构：** 使用 `DocumentStorage` 隔离 MinIO 与历史本地文件；上传采用“创建占位文档 → MinIO 上传 → 数据库事务完成权限和 Outbox”的补偿流程。向量 metadata 使用 `aclRole_<ROLE>=true` 和 `aclVersion`，检索器按当前角色强过滤并在文档进入模型前查询数据库复核。前端通过后端 Blob 接口打开原文件，不接触 MinIO 地址。

**技术栈：** Spring Boot 3.5、Spring AI 1.1、MinIO Java SDK、PostgreSQL/PGVector、MyBatis-Plus、Vue 3、Pinia、Element Plus、Node Test Runner。

---

## 文件结构

### 新建

- `smrag-core/src/main/resources/db/migration/V6__minio_storage_and_document_acl.sql`：文档存储字段、ACL 版本和 PGVector metadata 更新所需索引。
- `smrag-core/src/main/java/com/hqc/smrag/config/MinioStorageProperties.java`：绑定写死在 YAML 中的 MinIO 配置。
- `smrag-core/src/main/java/com/hqc/smrag/config/MinioStorageConfig.java`：创建 `MinioClient` 并初始化私有 Bucket。
- `smrag-core/src/main/java/com/hqc/smrag/service/storage/DocumentStorage.java`：对象存储边界。
- `smrag-core/src/main/java/com/hqc/smrag/service/storage/StoredObject.java`：流、长度、类型等读取结果。
- `smrag-core/src/main/java/com/hqc/smrag/service/storage/MinioDocumentStorage.java`：MinIO 上传、范围读取、临时物化和删除。
- `smrag-core/src/main/java/com/hqc/smrag/service/storage/DocumentSourceResolver.java`：MinIO 对象与历史本地路径的统一读取。
- `smrag-core/src/main/java/com/hqc/smrag/service/DocumentAccessService.java`：数据库权限查询和批量复核。
- `smrag-core/src/main/java/com/hqc/smrag/service/VectorAclMetadataService.java`：更新 PGVector metadata ACL 副本。
- `smrag-core/src/main/java/com/hqc/smrag/service/chat/DocumentAccessScope.java`：RAG 请求的用户 ID、角色和知识库权限上下文。
- `smrag-core/src/main/java/com/hqc/smrag/web/HttpRange.java`：解析单段 HTTP Range。
- `smrag-web/src/components/DocumentViewer.vue`：PDF/TXT 阅读器对话框。
- `smrag-web/src/utils/documentViewer.js`：文件类型、页码 URL 和 Blob 生命周期纯函数。
- 对应的后端及前端测试文件。

### 修改

- `pom.xml`、`smrag-core/pom.xml`：MinIO SDK 版本及依赖。
- `application.yml`：用户给定的 MinIO 固定配置。
- `KnowledgeDocument`、`DocumentVO`、`CitationDTO`：存储、ACL 和查看信息。
- `DocumentService`、`DocumentUploadPersistenceService`：上传补偿流程和对象删除。
- `DocumentIngestionService`、`DocumentIngestionPersistenceService`、`DocumentChunker`、`VectorReindexRunner`：MinIO 读取、解析文本存储和 ACL metadata。
- `DocPermissionMapper`、`DocPermissionService`、`PermissionController`：空权限拒绝及权限/向量 metadata 同步。
- `KnowledgeChatClient`、`SpringAiKnowledgeChatClient`、`KnowledgeQaService`、`CitationAssembler`、`AiChatService`：角色过滤和两次数据库复核。
- `KnowledgeController`：原文件读取接口。
- `request.js`、`api/knowledge.js`、`Knowledge.vue`、`Chat.vue`：Blob 响应、阅读器和引用点击。

---

### 任务 1：数据库与 MinIO 基础设施

**文件：**
- 修改：`pom.xml`
- 修改：`smrag-core/pom.xml`
- 修改：`smrag-core/src/main/resources/application.yml`
- 新建：`smrag-core/src/main/resources/db/migration/V6__minio_storage_and_document_acl.sql`
- 修改：`smrag-core/src/main/java/com/hqc/smrag/entity/KnowledgeDocument.java`
- 新建：`smrag-core/src/main/java/com/hqc/smrag/config/MinioStorageProperties.java`
- 新建：`smrag-core/src/main/java/com/hqc/smrag/config/MinioStorageConfig.java`
- 测试：`smrag-core/src/test/java/com/hqc/smrag/config/MinioStoragePropertiesTest.java`
- 测试：`smrag-core/src/test/java/com/hqc/smrag/integration/RagSchemaMigrationTest.java`

- [ ] **步骤 1：编写失败测试**

验证以下配置能绑定，并且迁移后的 `knowledge_document` 存在四个字段：

```java
assertThat(properties.getEndpoint()).isEqualTo("http://127.0.0.1:9000");
assertThat(properties.getBucketName()).isEqualTo("super-agent-document");
assertThat(columns).contains("storage_type", "object_key",
    "parsed_text_object_key", "acl_version");
```

- [ ] **步骤 2：运行测试确认 RED**

运行：`mvn -pl smrag-core -Dtest=MinioStoragePropertiesTest,RagSchemaMigrationTest test`

预期：属性类或 V6 字段不存在导致失败。

- [ ] **步骤 3：实现最小配置和迁移**

根 POM 增加 `<minio.version>8.5.17</minio.version>`，core 增加 `io.minio:minio`。YAML 使用已确认的固定值：

```yaml
smrag:
  storage:
    minio:
      endpoint: http://127.0.0.1:9000
      access-key: minioadmin
      secret-key: minioadmin
      bucket-name: ${prefix.distinction.name:super-agent}-document
      object-prefix: rag/document
      parsed-text-prefix: rag/parsed-text
```

迁移执行：

```sql
ALTER TABLE knowledge_document ADD COLUMN IF NOT EXISTS storage_type VARCHAR(16) NOT NULL DEFAULT 'LOCAL';
ALTER TABLE knowledge_document ADD COLUMN IF NOT EXISTS object_key VARCHAR(1024);
ALTER TABLE knowledge_document ADD COLUMN IF NOT EXISTS parsed_text_object_key VARCHAR(1024);
ALTER TABLE knowledge_document ADD COLUMN IF NOT EXISTS acl_version INTEGER NOT NULL DEFAULT 1;
CREATE INDEX IF NOT EXISTS idx_doc_permission_role_document
  ON doc_permission(role_id, document_id) WHERE can_read = TRUE;
```

`MinioStorageConfig` 使用 `bucketExists`/`makeBucket` 初始化 Bucket，不设置匿名策略。

- [ ] **步骤 4：运行测试确认 GREEN**

运行：`mvn -pl smrag-core -Dtest=MinioStoragePropertiesTest,RagSchemaMigrationTest test`

- [ ] **步骤 5：提交**

```bash
git add pom.xml smrag-core/pom.xml smrag-core/src/main/resources smrag-core/src/main/java/com/hqc/smrag/config smrag-core/src/main/java/com/hqc/smrag/entity/KnowledgeDocument.java smrag-core/src/test
git commit -m "feat: 增加 MinIO 文档存储配置"
```

### 任务 2：实现对象存储边界和历史文件兼容

**文件：**
- 新建：`smrag-core/src/main/java/com/hqc/smrag/service/storage/DocumentStorage.java`
- 新建：`smrag-core/src/main/java/com/hqc/smrag/service/storage/StoredObject.java`
- 新建：`smrag-core/src/main/java/com/hqc/smrag/service/storage/MinioDocumentStorage.java`
- 新建：`smrag-core/src/main/java/com/hqc/smrag/service/storage/DocumentSourceResolver.java`
- 测试：`smrag-core/src/test/java/com/hqc/smrag/service/storage/MinioDocumentStorageTest.java`
- 测试：`smrag-core/src/test/java/com/hqc/smrag/service/storage/DocumentSourceResolverTest.java`

- [ ] **步骤 1：编写失败测试**

定义期望接口：

```java
interface DocumentStorage {
    void put(String objectKey, InputStream stream, long size, String contentType);
    StoredObject get(String objectKey, Long offset, Long length);
    Path materialize(String objectKey, String suffix);
    void delete(String objectKey);
}
```

测试对象键由服务端生成、`materialize` 在失败时清理半成品、Resolver 对 `LOCAL` 返回原路径而对 `MINIO` 返回临时文件。

- [ ] **步骤 2：运行测试确认 RED**

运行：`mvn -pl smrag-core -Dtest=MinioDocumentStorageTest,DocumentSourceResolverTest test`

- [ ] **步骤 3：实现最小存储服务**

使用 MinIO SDK 的 `PutObjectArgs`、`GetObjectArgs.offset/length`、`StatObjectArgs` 和 `RemoveObjectArgs`。临时文件通过 `Files.createTempFile("smrag-document-", suffix)` 创建；所有流使用 try-with-resources。

- [ ] **步骤 4：运行测试确认 GREEN**

运行同上，并执行 `git diff --check`。

- [ ] **步骤 5：提交**

```bash
git add smrag-core/src/main/java/com/hqc/smrag/service/storage smrag-core/src/test/java/com/hqc/smrag/service/storage
git commit -m "feat: 实现 MinIO 文档存储边界"
```

### 任务 3：重构上传为可补偿的 MinIO 流程

**文件：**
- 修改：`DocumentService.java`
- 修改：`DocumentUploadPersistenceService.java`
- 修改：`KnowledgeController.java`
- 修改：`DocumentParseTaskDTO.java`
- 测试：`DocumentServiceTest.java`
- 测试：`DocumentUploadPersistenceServiceTest.java`

- [ ] **步骤 1：编写失败测试**

覆盖：空角色列表在写 MinIO 前拒绝；对象键格式为 `rag/document/{id}/{uuid}-{name}`；持久化失败后删除对象和占位文档；Outbox 只在对象键和权限均保存后创建。

```java
assertThatThrownBy(() -> service.uploadDocument(file, "default", List.of()))
    .isInstanceOf(IllegalArgumentException.class);
verify(storage, never()).put(any(), any(), anyLong(), any());
```

- [ ] **步骤 2：运行测试确认 RED**

运行：`mvn -pl smrag-core -Dtest=DocumentServiceTest,DocumentUploadPersistenceServiceTest test`

- [ ] **步骤 3：实现上传编排**

把 controller 中“上传后再设置权限”移入服务层。`DocumentUploadPersistenceService` 提供三个事务方法：创建占位记录、完成对象键/权限/Outbox、删除失败占位记录。`DocumentService` 捕获失败并尽力删除 MinIO 对象和占位记录。

- [ ] **步骤 4：运行测试确认 GREEN**

运行同上。

- [ ] **步骤 5：提交**

```bash
git add smrag-core/src/main/java/com/hqc/smrag/controller/KnowledgeController.java smrag-core/src/main/java/com/hqc/smrag/dto/DocumentParseTaskDTO.java smrag-core/src/main/java/com/hqc/smrag/service/knowledge smrag-core/src/test/java/com/hqc/smrag/service/knowledge
git commit -m "feat: 将文档上传切换到 MinIO"
```

### 任务 4：解析文本存储和 chunk ACL 一致性

**文件：**
- 修改：`DocumentIngestionService.java`
- 修改：`DocumentIngestionPersistenceService.java`
- 修改：`DocumentChunker.java`
- 修改：`VectorReindexRunner.java`
- 修改：`DocPermissionMapper.java`
- 测试：`DocumentIngestionServiceTest.java`
- 测试：`DocumentChunkerTest.java`
- 测试：`VectorReindexRunnerTest.java`

- [ ] **步骤 1：编写失败测试**

验证消费端通过 Resolver 获取临时文件；完整文本写到 `rag/parsed-text/{id}/{version}.txt`；每个 chunk 都包含同一 ACL：

```java
assertThat(chunk.getMetadata())
    .containsEntry("aclVersion", 2)
    .containsEntry("aclRole_ADMIN", true)
    .doesNotContainKey("aclRole_USER");
```

角色列表为空时 ingestion 失败且不调用 `vectorStore.add`。验证 MinIO 临时文件在成功和异常路径都删除。

- [ ] **步骤 2：运行测试确认 RED**

运行：`mvn -pl smrag-core -Dtest=DocumentIngestionServiceTest,DocumentChunkerTest,VectorReindexRunnerTest test`

- [ ] **步骤 3：实现 ACL metadata**

`DocPermissionMapper` 增加按文档读取角色编码。`DocumentChunker.chunk` 接收 `aclVersion` 和非空 `Set<String> roleCodes`，为所有源文档写入动态布尔字段。重建索引使用相同构造函数，保证正常解析和重建结果一致。

解析后的完整文本按源文档顺序用换行连接后以 UTF-8 上传，成功落库 `parsedTextObjectKey`。

- [ ] **步骤 4：运行测试确认 GREEN**

运行同上。

- [ ] **步骤 5：提交**

```bash
git add smrag-core/src/main/java/com/hqc/smrag/mapper/DocPermissionMapper.java smrag-core/src/main/java/com/hqc/smrag/service/knowledge smrag-core/src/test/java/com/hqc/smrag/service/knowledge
git commit -m "feat: 为文档分块写入权限元数据"
```

### 任务 5：权限修改原子同步 PGVector metadata

**文件：**
- 新建：`VectorAclMetadataService.java`
- 修改：`DocPermissionService.java`
- 修改：`DocPermissionMapper.java`
- 修改：`PermissionController.java`
- 测试：`VectorAclMetadataServiceTest.java`
- 修改测试：`DocPermissionServiceTest.java`

- [ ] **步骤 1：编写失败测试**

验证空权限在删除旧权限之前失败；合法更新增加 `aclVersion`；更新 JSON 会清除旧 `aclRole_*` 并写入新字段；任一 SQL 失败导致权限事务回滚。

- [ ] **步骤 2：运行测试确认 RED**

运行：`mvn -pl smrag-core -Dtest=DocPermissionServiceTest,VectorAclMetadataServiceTest test`

- [ ] **步骤 3：实现事务同步**

`setDocumentPermissions` 加载并锁定文档，拒绝空集合，替换权限后调用 `VectorAclMetadataService.replaceAcl(documentId, nextVersion, roleCodes)`。更新 SQL 以 `metadata->>'documentId'` 定位记录，先过滤全部 `aclRole_` 键，再合并新的 ACL JSON 和版本；metadata 列保持原有 JSON 类型兼容。

- [ ] **步骤 4：运行测试确认 GREEN**

运行同上。

- [ ] **步骤 5：提交**

```bash
git add smrag-core/src/main/java/com/hqc/smrag/controller/PermissionController.java smrag-core/src/main/java/com/hqc/smrag/mapper/DocPermissionMapper.java smrag-core/src/main/java/com/hqc/smrag/service smrag-core/src/test/java/com/hqc/smrag/service
git commit -m "feat: 同步文档权限与向量元数据"
```

### 任务 6：RAG 角色强过滤和模型前复核

**文件：**
- 新建：`DocumentAccessScope.java`
- 新建：`DocumentAccessService.java`
- 修改：`KnowledgeChatClient.java`
- 修改：`SpringAiKnowledgeChatClient.java`
- 修改：`KnowledgeQaService.java`
- 修改：`AiChatService.java`
- 修改：`CitationAssembler.java`
- 测试：`SpringAiKnowledgeChatClientTest.java`
- 测试：`KnowledgeQaServiceTest.java`
- 测试：`CitationAssemblerTest.java`

- [ ] **步骤 1：编写失败测试**

验证角色 `RESEARCHER` 生成 `aclRole_RESEARCHER == true`；角色为空生成 `documentId == -1`；指定文档条件仍与 ACL 使用 AND；检索返回的无权限文档在传给模型前移除；引用组装再次批量复核。

- [ ] **步骤 2：运行测试确认 RED**

运行：`mvn -pl smrag-core -Dtest=SpringAiKnowledgeChatClientTest,KnowledgeQaServiceTest,CitationAssemblerTest test`

- [ ] **步骤 3：替换文档 ID OR 过滤**

从当前认证对象提取 `userId` 和单一角色构造 `DocumentAccessScope`。`SpringAiKnowledgeChatClient` 的过滤条件固定包含：

```java
filters.and(baseFilter, filters.eq("aclRole_" + scope.roleCode(), true))
```

用一个复核型 `DocumentRetriever` 包装向量 retriever：先取回候选文档，收集 documentId，通过 `DocumentAccessService.retainReadable` 批量查询后再返回给 `RetrievalAugmentationAdvisor`，确保无权限内容不会进入模型提示词。`KnowledgeQaService` 在组装引用前再次调用同一复核服务。

- [ ] **步骤 4：运行测试确认 GREEN**

运行同上，并运行 `mvn -pl smrag-core -Dtest=AiChatServiceTest,AiChatServiceWiringTest test`。

- [ ] **步骤 5：提交**

```bash
git add smrag-core/src/main/java/com/hqc/smrag/service/chat smrag-core/src/main/java/com/hqc/smrag/service/DocumentAccessService.java smrag-core/src/test/java/com/hqc/smrag/service/chat
git commit -m "feat: 强化 RAG 检索与引用权限校验"
```

### 任务 7：受保护的原文件读取接口

**文件：**
- 新建：`HttpRange.java`
- 修改：`KnowledgeController.java`
- 修改：`DocumentService.java`
- 修改：`DocumentVO.java`
- 修改：`CitationDTO.java`
- 测试：`HttpRangeTest.java`
- 测试：`KnowledgeContentControllerTest.java`

- [ ] **步骤 1：编写失败测试**

覆盖：无权限返回 403 且不读取存储；PDF/TXT 为 inline；Word 为 attachment；PDF `Range: bytes=0-99` 返回 206、`Content-Range` 和 100 字节；历史 LOCAL 文件仍能读取。

- [ ] **步骤 2：运行测试确认 RED**

运行：`mvn -pl smrag-core -Dtest=HttpRangeTest,KnowledgeContentControllerTest test`

- [ ] **步骤 3：实现内容响应**

接口为 `GET /api/knowledge/{id}/content`。先调用 `DocumentAccessService.requireReadable(userId, id)`，再解析 Range 并读取存储。响应设置 `Accept-Ranges: bytes`、准确 Content-Type、RFC 5987 编码文件名及 `X-Content-Type-Options: nosniff`。`CitationDTO` 增加 `documentType`，前端无需额外请求即可选择阅读或下载。

- [ ] **步骤 4：运行测试确认 GREEN**

运行同上和 `mvn -pl smrag-core -Dtest=ChatContractTest,CitationAssemblerTest test`。

- [ ] **步骤 5：提交**

```bash
git add smrag-core/src/main/java/com/hqc/smrag/controller/KnowledgeController.java smrag-core/src/main/java/com/hqc/smrag/dto/CitationDTO.java smrag-core/src/main/java/com/hqc/smrag/service/knowledge/DocumentService.java smrag-core/src/main/java/com/hqc/smrag/vo/DocumentVO.java smrag-core/src/main/java/com/hqc/smrag/web smrag-core/src/test
git commit -m "feat: 提供鉴权文档内容接口"
```

### 任务 8：前端文档阅读器和引用跳转

**文件：**
- 修改：`smrag-web/src/utils/request.js`
- 修改：`smrag-web/src/api/knowledge.js`
- 新建：`smrag-web/src/utils/documentViewer.js`
- 新建：`smrag-web/src/components/DocumentViewer.vue`
- 修改：`smrag-web/src/views/Knowledge.vue`
- 修改：`smrag-web/src/views/Chat.vue`
- 新建：`smrag-web/tests/documentViewer.test.js`

- [ ] **步骤 1：编写失败测试**

纯函数测试覆盖：PDF 页码 Blob URL、TXT 类型判定、Word 下载判定、关闭时调用 `URL.revokeObjectURL`、无权限文档不可打开。

```javascript
assert.equal(withPdfPage('blob:test', 7), 'blob:test#page=7')
assert.equal(viewerMode('WORD'), 'download')
```

- [ ] **步骤 2：运行测试确认 RED**

运行：`cd smrag-web && npm.cmd test`

- [ ] **步骤 3：实现阅读器**

`request.js` 支持 `rawResponse: true`，以便 Blob API 同时读取 headers。`getDocumentContent(id)` 使用 `responseType: 'blob'`。`DocumentViewer` 接收 `{id, name, fileType, pageNumber}`：PDF 使用 iframe，TXT 使用 `blob.text()`，Word 创建隐藏 `<a download>`；关闭时释放 Blob URL。

知识库列表仅在 `row.accessible` 时启用“查看原文”。Chat 引用标题改为按钮并传入引用页码；403 使用“你已没有该文档的访问权限”提示。

- [ ] **步骤 4：运行测试和构建确认 GREEN**

运行：

```bash
cd smrag-web
npm.cmd test
npm.cmd run build
```

- [ ] **步骤 5：提交**

```bash
git add smrag-web/src smrag-web/tests
git commit -m "feat: 增加文档阅读器与引用跳转"
```

### 任务 9：删除补偿与全量验证

**文件：**
- 修改：`DocumentService.java`
- 修改：`DocumentServiceTest.java`
- 按失败结果修复上述任务涉及的文件。

- [ ] **步骤 1：编写删除失败路径测试**

验证数据库提交后同时删除 `objectKey` 和 `parsedTextObjectKey`；MinIO 删除异常只记录清理失败，不恢复已删除数据库记录；历史 LOCAL 文件仍使用本地删除。

- [ ] **步骤 2：运行目标测试确认 RED，再实现 GREEN**

运行：`mvn -pl smrag-core -Dtest=DocumentServiceTest test`

- [ ] **步骤 3：运行后端全量验证**

```bash
mvn -pl smrag-core test
```

预期：全部测试通过，`BUILD SUCCESS`。

- [ ] **步骤 4：运行前端全量验证**

```bash
cd smrag-web
npm.cmd test
npm.cmd run build
```

预期：Node 测试全部通过，Vite 构建成功。

- [ ] **步骤 5：检查迁移和工作区**

```bash
git diff --check
git status --short
```

确认不修改用户现有的未跟踪文件 `docs/技术文档.md`，不提交 MinIO 凭据之外的本地配置或构建产物。

- [ ] **步骤 6：最终提交**

```bash
git add smrag-core smrag-web
git commit -m "test: 完善 MinIO 文档权限回归测试"
```

---

## 验收标准

- MinIO Bucket 缺失时应用自动创建，且不公开访问。
- 新上传原文件和解析文本出现在约定对象前缀下。
- 上传权限为空时在解析和向量写入之前失败。
- 每个 chunk 包含一致的 ACL metadata；权限修改后 metadata 和版本同步更新。
- 无权限 chunk 不进入模型上下文，也不出现在引用中。
- 无权限用户无法通过当前或历史引用读取原文件。
- PDF/TXT 可以在线阅读，PDF 引用能跳页，Word 能下载。
- 删除文档会清理原文件和解析文本。
- 历史本地文件仍可解析和读取。
- 后端全量测试、前端测试和生产构建全部通过。
