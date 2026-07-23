# MinIO 文档存储、在线阅读与 RAG 权限设计

## 目标

将新上传的原始文档和解析后的纯文本存入本地 MinIO，在知识库和问答引用中提供受权限保护的原文件访问，并把文档角色权限同步到每个向量 chunk，在检索、模型上下文、引用返回和文件读取四个边界实施强制校验。

## 范围

- 新上传的 PDF、DOC、DOCX、TXT 原文件存入 MinIO。
- 解析后的完整纯文本单独存入 MinIO，供切块、重建索引和故障恢复使用。
- PDF 和 TXT 支持前端在线阅读；DOC、DOCX 提供下载。
- 文档列表和问答引用可以打开同一个阅读器。
- 保留已有本地文件路径的读取兼容性，不迁移历史文件。
- 不引入 Office 转 PDF、公开 Bucket 或前端直连 MinIO。

## MinIO 配置

配置直接写入 `application.yml`：

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

应用启动时检查 Bucket，不存在则创建。Bucket 不设置公开读取策略。

## 对象布局

```text
super-agent-document/
├─ rag/document/{documentId}/{uuid}-{sanitizedFileName}
└─ rag/parsed-text/{documentId}/{version}.txt
```

数据库记录原文件对象键和解析文本对象键，不保存 MinIO 预签名 URL。对象键由后端生成，不能接受客户端传入。

## 存储边界

新增 `DocumentStorage` 抽象，负责上传、读取、下载到临时文件、删除和对象元数据查询。MinIO 是新上传文件的默认实现。已有绝对或相对本地路径通过兼容读取器继续可用，避免历史文档失效。

异步解析依赖本地 `Path` 的现有 Spring AI Reader，因此 MinIO 文件在消费端物化为临时文件，解析结束后无论成功或失败都删除临时文件。

## 上传与解析流程

1. 管理员提交文件、知识库 ID 和非空角色列表。
2. 后端校验文件名、类型、大小和全部角色。
3. 创建文档记录以获得 `documentId`，状态为 `PENDING`，但尚不发布解析任务。
4. 原文件上传到 MinIO，并计算 SHA-256。
5. 数据库事务更新对象键、校验和、权限、版本、处理令牌并写入 Outbox。
6. 任一步失败时删除已上传对象；没有完整权限的文档不得进入解析队列。
7. Kafka 消费者下载原文件到临时目录并解析出完整文本。
8. 完整文本上传到 `rag/parsed-text/{documentId}/{version}.txt`。
9. 切块时为每个 chunk 注入相同的 ACL metadata，然后写入 PGVector 和 `knowledge_chunk`。
10. 处理成功后更新文档状态和解析文本对象键。

解析文本上传成功但后续向量写入失败时保留解析文本，以便重试复用；文档删除时统一清理。

## 权限元数据

每个 chunk 至少包含：

```json
{
  "documentId": 1001,
  "knowledgeBaseId": "default",
  "documentStatus": "COMPLETED",
  "aclVersion": 3,
  "aclRole_ADMIN": true,
  "aclRole_RESEARCHER": true
}
```

角色编码已经受字母、数字和下划线约束，可安全映射为 metadata key。采用角色布尔字段，而不是依赖 JSON 数组包含语义，检索条件可以稳定表达为 `aclRole_<currentRole> == true`。

空角色列表在上传和权限修改接口中均视为非法请求。运行时缺少已认证用户、角色或有效 ACL 时，检索器使用永不命中的条件，绝不能退化为不带权限过滤的查询。

## 权限修改与同步

权限表是权威数据源，向量 metadata 是检索优化副本。

权限修改在同一个 PostgreSQL 事务中完成：

1. 校验文档和全部角色。
2. 替换 `doc_permission` 关系。
3. 增加文档 `acl_version`。
4. 使用 SQL 更新 `rag_vector_store` 中该文档所有记录的 ACL metadata，移除旧 `aclRole_*` 字段并写入新字段和版本。

事务失败则数据库权限与向量 metadata 一起回滚。已有未带 ACL 的旧向量不会被新检索条件命中，需要通过重建索引补齐权限 metadata。

## RAG 强制校验

权限链路依次为：

```text
数据库角色权限
→ chunk ACL metadata
→ PGVector 检索强过滤
→ 进入模型前数据库复核
→ 模型生成
→ 引用返回前数据库复核
→ 原文件读取时再次复核
```

- 检索不再构造全部可访问文档 ID 的 OR 条件，而是使用当前用户角色对应的 ACL metadata 条件。
- 指定 `documentId` 时仍必须同时满足角色 ACL。
- 检索结果在注入模型上下文前按数据库当前权限过滤，防止 metadata 同步延迟或历史数据异常造成泄露。
- `CitationAssembler` 只组装复核后仍可访问的结果。
- 历史会话中的引用只是文档 ID 和页码提示，点击时必须重新鉴权。

## 缓存规则

当前项目没有独立的 RAG 答案缓存，本次不新增缓存组件。以后引入缓存时，缓存键必须包含用户 ID、排序后的角色摘要和 ACL 版本；权限修改后必须失效相关缓存。禁止仅以问题文本或知识库 ID 作为缓存键。

## 原文件访问接口

新增受认证保护的接口：

```text
GET /api/knowledge/{documentId}/content
```

处理顺序：

1. 根据 JWT 取得用户 ID。
2. 查询数据库确认用户对文档具有读取权限。
3. 无权限统一返回 403，不返回对象键、文件名或 MinIO 信息。
4. 从 MinIO 或兼容本地存储读取原文件。
5. PDF、TXT 使用 `Content-Disposition: inline`；DOC、DOCX 使用 `attachment`。
6. 设置准确的 Content-Type、安全的文件名和 `X-Content-Type-Options: nosniff`。

PDF 接口支持 Range 请求，避免大文件必须完整下载后才能翻页。MinIO 地址和凭据永不返回前端。

## 前端阅读器

- 文档列表对有权限的文档显示“查看原文”。无权限文档仍可见，但按钮禁用并提示无权限。
- PDF：携带 JWT 请求后端，转换为 Blob URL，在对话框内使用浏览器 PDF 阅读器展示；引用包含页码时追加 `#page=N`。
- TXT：获取文本并在可滚动、保留换行的阅读区域展示。
- DOC、DOCX：触发受保护接口下载。
- 问答引用的文档名称和页码可点击，复用同一打开逻辑。
- 关闭阅读器时释放 Blob URL，防止内存泄漏；401/403 时关闭加载状态并显示明确提示。

## 删除与补偿

删除文档时先在数据库标记 `DELETING`，删除向量和 chunk，提交后删除原文件及解析文本对象。MinIO 删除失败不恢复数据库文档，而是记录可重试的清理失败日志，避免用户继续访问已删除文档。

上传阶段若数据库事务失败，立即尽力删除 MinIO 原文件。解析临时文件始终在 `finally` 中清理。

## 数据库变更

为 `knowledge_document` 增加：

- `storage_type`：`LOCAL` 或 `MINIO`。
- `object_key`：原文件对象键。
- `parsed_text_object_key`：解析文本对象键。
- `acl_version`：权限版本，默认 1。

保留 `file_path` 用于历史本地文件兼容。新 MinIO 文档不依赖 `file_path`。

## 测试

- 存储单元测试：Bucket 初始化、上传、临时下载、Range 读取和删除。
- 上传事务测试：空权限拒绝、对象上传失败、数据库失败后的对象补偿删除。
- 解析测试：MinIO 文件物化、解析文本上传、临时文件清理、chunk ACL 一致性。
- 权限测试：空权限不放行、角色 metadata 强过滤、指定文档仍校验 ACL、权限变更同步 metadata。
- 引用测试：进入模型前和引用组装前移除无权限文档。
- 内容接口测试：有权限返回、无权限 403、PDF inline、Word attachment、历史本地文件兼容。
- 前端测试：PDF/TXT 打开、Word 下载、引用页码跳转、403 提示和 Blob URL 释放。
- 最终运行后端全量测试、前端测试和生产构建。

## 非目标

- 不公开 MinIO Bucket。
- 不向前端发放预签名 URL。
- 不实现 DOC/DOCX 在线渲染或转 PDF。
- 不自动迁移历史本地文件到 MinIO。
- 不新增 Redis 或其他问答缓存。
