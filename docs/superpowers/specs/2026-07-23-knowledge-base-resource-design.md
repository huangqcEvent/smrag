# 知识库资源化设计

## 目标

移除用户可见及业务代码中的 `default` 知识库，把知识库建模为可查询的一等资源。界面展示名称，上传、检索和向量元数据使用稳定 code。

## 数据模型

新增 `knowledge_base` 表：

- `code`：稳定主键，首个知识库为 `company-main`
- `name`：用户可见名称，首个知识库为“企业知识库”
- `description`：用途说明
- `status`：`ACTIVE` 或 `DISABLED`
- `created_at`、`updated_at`：审计时间

`knowledge_document.knowledge_base_id` 保持字符串类型，但增加到 `knowledge_base.code` 的外键。这样现有文档和 RAG 元数据可以统一使用同一个稳定 code，不依赖可修改的展示名称。

## 后端行为

- `GET /api/knowledge-bases` 返回所有启用的知识库。
- 空知识库 code 和旧值 `default` 在兼容入口统一归一化为 `company-main`。
- 上传、列表、删除、RAG 检索和原文件读取都会校验知识库真实存在且处于启用状态。
- 聊天问答、文档切块、重建索引和向量过滤全部使用稳定 code。
- 迁移会把向量 metadata 中的知识库 code 同步为文档表的权威值，并先登记历史自定义 code，再添加外键。
- 文档权限仍以 `documentId + roleId` 为权威来源；知识库 code 只负责数据域隔离，不替代文档 ACL。

## 前端行为

- 知识库管理页和聊天页使用下拉选择器，不允许用户手输内部 ID。
- 选择器展示后端返回的知识库名称，请求只发送稳定 code。
- 首次加载、接口失败或旧状态为 `default` 时，统一回退到 `company-main`。
- 登录、登出会清空文档状态并使旧请求失效；切换知识库时旧请求不能覆盖新选择。
- 文档列表继续展示文档角色权限；没有文档访问权限时仍显示文档但禁用阅读。

## 数据清理

本地环境现有知识数据已被明确清理：

- PostgreSQL：`knowledge_document`、`knowledge_chunk`、`rag_vector_store`、`doc_permission`、`document_ingestion_outbox`
- MinIO：`super-agent-document/rag/document`、`super-agent-document/rag/parsed-text`

账号、角色、聊天会话以及 MinIO bucket 本身不在清理范围内。

## 验证

- 数据库迁移能创建并种子化“企业知识库”，文档默认值和外键正确。
- 后端单元测试覆盖归一化、启用状态校验和知识库列表接口。
- 文档上传与问答测试断言默认 code 为 `company-main`。
- 前端测试覆盖知识库选择、接口返回归一化和账号状态重置。
- Maven 全量测试、前端测试和生产构建均通过。
