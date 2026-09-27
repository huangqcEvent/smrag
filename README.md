# FundRAG

云端部署请参阅 [docs/deployment.md](docs/deployment.md)。

基于RAG的私募基金智能问答平台。

## 技术栈
- SpringBoot 3.3.x
- SpringAI + SpringAI Alibaba
- PostgreSQL + PGVector
- Apache Tika
- Kafka

## 快速开始
本地开发（Windows 便携版中间件，无 Docker）按 [docs/16-本地启动.md](docs/16-本地启动.md) 操作：

1. 启动 PostgreSQL（pgvector）、Kafka、MinIO
2. 建库 `smrag`，Flyway 会在后端启动时自动执行 `db/migration` 下的迁移脚本
3. 设置环境变量 `DASHSCOPE_API_KEY`
4. 启动后端（8080）与前端（3000）

## 功能
- 知识库管理：文档上传、解析、向量化
- 智能问答：问题改写、路由分类、RAG检索
- ToolCalling：产品查询、管理人查询、对比
