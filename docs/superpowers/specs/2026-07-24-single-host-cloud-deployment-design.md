# FundRAG 单机云部署设计

## 目标

为面试演示和首个线上版本提供一套可重复、可升级、默认不暴露内部组件的部署方案。目标环境是一台 Ubuntu 24.04 云服务器，最低 4 核 8 GB，推荐 8 核 16 GB。

## 架构

```text
Internet
   |
80 / 443
   |
Caddy（自动 HTTPS + Vue 静态资源）
   |
Spring Boot API
   +-- PostgreSQL + PGVector
   +-- Kafka（单节点 KRaft）
   +-- MinIO
   +-- DashScope（公网出站）
```

只有 Caddy 发布宿主机端口。PostgreSQL、Kafka、MinIO 和后端仅通过 Docker 网络互通，避免数据库、消息队列和对象存储直接暴露到公网。

## 关键决策

- 使用一个 `compose.prod.yml` 管理所有服务，降低首次上线和面试复现成本。
- Caddy 同时托管前端和反向代理 `/api/*`，前后端同源，不需要额外 CORS 配置。
- Caddy 对 API 使用即时刷新，保证 SSE 问答输出不会被代理缓冲。
- 生产配置通过 `.env.prod` 注入；数据库密码、MinIO 密钥、JWT 密钥和 DashScope Key 不提供可运行默认值。
- Spring Boot 增加最小化健康检查，仅公开 `/actuator/health`，不公开环境和配置详情。
- 数据持久化使用命名卷；备份脚本同时导出 PostgreSQL 和 MinIO 对象。
- Kafka 使用单节点 broker/controller 合并模式，适合演示和首期小流量，不宣称高可用。

## 安全边界

- 云安全组只开放 80、443；22 端口限制为管理员 IP。
- 内部服务不设置 `ports`。
- 生产日志降为 INFO，健康检查不显示组件明细。
- `.env.prod` 被 Git 忽略，仓库只保留不含真实秘密的示例文件。
- 上传、文档预览和引用查看仍统一经过后端权限校验，不绕过后端直接访问 MinIO。

## 运维边界

这是一套单机生产起步方案，不提供跨节点高可用。服务数量、文档规模或并发显著增长后，应优先迁移 PostgreSQL、对象存储和 Kafka 到托管服务，并将后端横向扩容。
