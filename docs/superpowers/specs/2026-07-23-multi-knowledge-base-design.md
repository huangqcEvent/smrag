# 多知识库与两层权限设计

## 1. 目标

把知识库从固定配置项改造成可管理的业务资源，并保持与现有用户、角色和文档权限体系兼容。

完成后的系统具备：

- 管理员新增、编辑、启用、停用和删除知识库。
- 知识库角色权限与文档角色权限两层校验。
- 文档上传、列表、下载、迁移和 RAG 检索均受统一权限服务控制。
- 一次问答显式选择 1–5 个知识库，各知识库独立召回后合并结果。
- 角色名称修改、用户换角色后立即生效，不需要重新生成向量。
- 系统初始不创建任何知识库，由管理员自行创建第一个知识库。

本次不做历史知识数据兼容。允许清空旧知识库、文档、分块、向量、文档权限、摄取 Outbox 和 MinIO 文档对象；保留用户、角色和聊天数据。

## 2. 核心约束

- `ADMIN` 是唯一全局管理角色。
- 其他角色，包括当前数据库中的 `SUPERADMIN`，都只是普通业务角色。
- 角色编码不可修改，角色名称和说明可以修改。
- 启用的知识库至少配置一个允许访问的角色。
- 文档允许访问的角色必须是所属知识库角色集合的子集。
- 空知识库权限或空文档权限均表示拒绝所有普通用户，不存在默认放行。
- 普通用户看不到无权访问或已停用的知识库。
- 用户有知识库权限但没有某份文档权限时，文档仍出现在列表中并显示“无权阅读”。
- 权限变更对新请求立即生效；已开始的流式请求使用请求开始时的权限快照。

## 3. 数据模型

### 3.1 knowledge_base

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | BIGSERIAL | 内部主键 |
| code | VARCHAR(64) | 唯一、创建后不可修改 |
| name | VARCHAR(128) | 展示名称 |
| description | VARCHAR(500) | 说明 |
| status | VARCHAR(16) | `ACTIVE` 或 `DISABLED` |
| version | INT | 管理操作乐观锁 |
| created_by | BIGINT | 创建人 |
| created_at | TIMESTAMP | 创建时间 |
| updated_at | TIMESTAMP | 修改时间 |

约束：

- `code` 只允许小写字母、数字和连字符。
- `code` 唯一。
- `name` 去除首尾空格后不能为空。
- 不存储文档数量，实时聚合，避免计数漂移。

### 3.2 knowledge_base_role

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| knowledge_base_id | BIGINT | 知识库外键 |
| role_id | BIGINT | 现有 `sys_role` 外键 |
| created_by | BIGINT | 授权人 |
| created_at | TIMESTAMP | 授权时间 |

联合主键为 `(knowledge_base_id, role_id)`。记录存在即表示允许访问，不增加冗余 `can_read` 字段。

### 3.3 knowledge_document

`knowledge_base_id` 改为 `BIGINT NOT NULL`，外键指向 `knowledge_base.id`，不再使用字符串默认值。

保留现有 `doc_permission(document_id, role_id, can_read)`，它继续负责文档级权限。

### 3.4 rag_vector_store

metadata 保留：

```json
{
  "knowledgeBaseId": 12,
  "documentId": 36,
  "documentVersion": 2,
  "documentAclVersion": 4,
  "chunkIndex": 8,
  "pageNumber": 3
}
```

不把角色名称、角色编码或完整角色集合写入 metadata。

增加由 metadata 生成的存储列：

- `knowledge_base_id BIGINT`
- `document_id BIGINT`
- `document_acl_version INT`

并为 `knowledge_base_id`、`document_id` 建立索引。生成列保证 Spring AI 写入 metadata 后，关系检索可以使用类型化字段，避免每次解析 JSON。

## 4. 权限模型

普通用户访问文档必须同时满足：

```text
用户当前角色 ∩ 知识库授权角色 ≠ 空
并且
用户当前角色 ∩ 文档授权角色 ≠ 空
```

管理员：

- 可以管理所有知识库。
- 可以查看停用知识库并迁移或删除其中的文档。
- 不能向停用知识库上传文档。
- RAG 仍只允许选择启用知识库。

新增统一的 `PermissionDecisionService`：

- `listAccessibleKnowledgeBases(userId)`
- `requireKnowledgeBaseAccess(userId, knowledgeBaseIds)`
- `requireDocumentRead(userId, documentId)`
- `validateDocumentRoles(knowledgeBaseId, roleIds)`
- `isGlobalAdmin(userId)`

现有 `DocPermissionService` 保留公开接口，内部逐步委托统一权限服务，避免控制器各自实现权限规则。

JWT 继续携带 `userId`，但授权判断从 `sys_user_role` 实时读取当前角色，不能把 JWT 中的角色声明作为权威数据。

## 5. 知识库生命周期

### 5.1 创建

- 仅 `ADMIN`。
- `code`、名称、说明和初始角色在一个事务中写入。
- 启用状态必须至少选择一个角色。
- 初始数据库没有内置知识库。

### 5.2 编辑

- `code` 不可修改。
- 名称、说明使用 `version` 做乐观锁。
- 替换角色集合时锁定知识库。
- 如果待移除角色仍被库内文档权限引用，拒绝并返回冲突文档数量。

### 5.3 停用和启用

- 停用后普通用户不可见、不可检索、不可下载。
- 管理员仍可进入管理、迁移或删除文档，但不可上传。
- 启用前必须至少配置一个角色。

### 5.4 删除

- 仅允许删除没有文档的知识库。
- 删除时使用 `SELECT ... FOR UPDATE` 锁定知识库，再次统计文档。
- 非空返回 `KNOWLEDGE_BASE_NOT_EMPTY`，不做级联文档删除。

### 5.5 文档迁移

- 仅 `ADMIN`。
- 目标知识库必须启用。
- 正在解析或删除中的文档不能迁移。
- 文档角色不是目标知识库角色子集时拒绝，并返回冲突角色。
- 同时锁定文档、源知识库和目标知识库，并按 ID 顺序加锁避免死锁。
- 在一个数据库事务中更新文档归属和向量 metadata。
- embedding 不重新计算，MinIO 对象不移动。

## 6. RAG 授权检索

新增 `AuthorizedPgVectorRetriever`，替代通用 `VectorStoreDocumentRetriever` 的权限过滤。

请求流程：

1. 接收 1–5 个知识库 code。
2. 根据 code 解析数值 ID，检查启用状态。
3. 使用当前 `userId` 从数据库读取实时角色。
4. 任一知识库不存在、停用或无权访问，整个请求失败。
5. 问题只生成一次 embedding。
6. 每个知识库分别执行授权向量查询并取候选。
7. 合并候选，按相似度排序并限制最终上下文数量。
8. 模型生成答案。
9. 引用返回前再次按当前权限校验。

权限必须在 TopK 前执行。核心查询同时关联：

- `rag_vector_store`
- `knowledge_document`
- `knowledge_base`
- `knowledge_base_role`
- `doc_permission`
- `sys_user_role`

查询使用 `EXISTS` 判断两层角色交集。空权限自然返回零行，没有放行分支。

多知识库检索按知识库独立召回，避免文档量大的知识库垄断全部候选。由于所有向量使用同一 embedding 模型和距离度量，第一版直接按相似度合并，不增加额外重排模型。

第一版不增加缓存：

- embedding 不缓存。
- 检索结果不缓存。
- 最终回答不缓存。
- 性能依靠索引、连接池、TopK 和最多 5 个知识库的范围限制。

## 7. API

### 7.1 管理接口

```text
GET    /api/admin/knowledge-bases
POST   /api/admin/knowledge-bases
PUT    /api/admin/knowledge-bases/{id}
PUT    /api/admin/knowledge-bases/{id}/roles
PATCH  /api/admin/knowledge-bases/{id}/status
DELETE /api/admin/knowledge-bases/{id}

POST   /api/admin/documents/{id}/move
```

管理列表包含知识库状态、角色、文档数量和版本。

### 7.2 普通用户接口

```text
GET /api/knowledge-bases
```

只返回当前用户可访问且启用的知识库。

### 7.3 文档接口

上传、列表和删除改用必填 `knowledgeBaseCode`，不存在隐式默认知识库。

### 7.4 问答接口

```json
{
  "question": "问题",
  "knowledgeBaseCodes": ["research", "compliance"]
}
```

- 必须选择 1–5 个知识库。
- 删除旧的单值 `knowledgeBaseId` 字段，不保留兼容分支。
- 引用增加 `knowledgeBaseId` 和 `knowledgeBaseName`。

## 8. 前端交互

### 8.1 知识库页面

- 左侧显示当前用户可访问的知识库。
- 右侧显示所选知识库的说明、上传区和文档列表。
- 只有一个知识库时显示固定名称，不显示无意义的下拉框。
- 管理员可新增知识库，并打开设置面板修改名称、说明、角色和状态。
- 管理员可以批量选择文档迁移到其他启用知识库。
- 非空知识库删除按钮禁用并展示原因。
- 停用知识库仅管理员可见，上传按钮禁用。
- 有知识库权限但没有文档权限的文档仍显示锁定状态。

### 8.2 聊天页面

- 只有一个可访问知识库时显示固定名称。
- 多个知识库时显示可搜索多选框，最多选择 5 个。
- 选择是账号级状态，登录和退出时清空。
- 权限变化导致选择失效时移除并提示，不自动替换。
- 引用展示“知识库 / 文档”，点击后实时校验文件权限。

### 8.3 空状态

- 管理员没有知识库时，引导创建第一个知识库。
- 普通用户没有可访问知识库时，提示联系管理员授权。
- 没有选择知识库时不能发起知识问答或上传文档。

## 9. 错误模型

```text
KNOWLEDGE_BASE_NOT_FOUND          404
KNOWLEDGE_BASE_FORBIDDEN          403
KNOWLEDGE_BASE_DISABLED           409
KNOWLEDGE_BASE_NOT_EMPTY          409
KNOWLEDGE_BASE_VERSION_CONFLICT   409
ROLE_STILL_IN_USE                 409
DOCUMENT_ROLE_OUTSIDE_BASE        409
DOCUMENT_PROCESSING               409
KNOWLEDGE_BASE_LIMIT_EXCEEDED     400
```

错误响应应包含稳定错误码和可展示的中文消息。冲突类错误可携带文档数量或角色编码列表。

## 10. 数据清理与最终建表

实施时：

- 删除当前单知识库实现对应的旧迁移内容，直接写入最终结构。
- 清空 `knowledge_document`、`knowledge_chunk`、`rag_vector_store`、`doc_permission` 和 `document_ingestion_outbox`。
- 清空 MinIO 的 `rag/document` 与 `rag/parsed-text` 前缀。
- 重建 `knowledge_base` 和 `knowledge_base_role`。
- 保留 `sys_user`、`sys_role`、`sys_user_role`、聊天记录和 MinIO bucket。
- 不创建初始知识库。

## 11. 测试与验收

### 后端单元测试

- code、名称、角色集合和状态校验。
- 普通用户、管理员、空权限和多角色权限判定。
- 文档权限必须为知识库权限子集。
- 角色移除冲突、非空删除、停用上传和迁移冲突。
- 问答必须选择 1–5 个知识库。

### PostgreSQL/PGVector 集成测试

- 最终 schema、外键、生成列和索引。
- 权限过滤发生在 TopK 前。
- 无知识库权限或无文档权限时召回零行。
- 修改用户角色后下一次查询立即变化。
- 多知识库分别召回并合并。
- 文档迁移后旧知识库无法检索，新知识库可以检索。

### 前端测试

- 空知识库状态。
- 单知识库固定展示和多知识库选择。
- 最多选择 5 个。
- 登录、退出和权限变化清理选择。
- 管理操作的禁用条件和错误提示。

### 验收

- 数据库中没有固定或默认知识库。
- 管理员可以完整管理多个知识库。
- 普通用户只能看到授权知识库。
- 文档列表、原文件、RAG 上下文和引用均遵守两层权限。
- 后端全量测试、前端测试和生产构建通过。
