# 私募AI专家系统

基于RAG的私募基金智能问答平台。

## 技术栈
- SpringBoot 3.3.x
- SpringAI + SpringAI Alibaba
- PostgreSQL + PGVector
- Apache Tika
- Kafka

## 快速开始
1. 配置环境变量 ALIBABA_API_KEY
2. 启动 PostgreSQL 和 Kafka
3. 运行 sql/init.sql 初始化数据库
4. 启动 SmragApplication

## 功能
- 知识库管理：文档上传、解析、向量化
- 智能问答：问题改写、路由分类、RAG检索
- ToolCalling：产品查询、管理人查询、对比