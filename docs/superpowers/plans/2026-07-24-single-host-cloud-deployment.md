# FundRAG 单机云部署实现计划

1. 新增部署契约测试，约束服务清单、端口暴露、秘密注入、健康检查、SSE 代理和备份能力。
2. 为 Spring Boot 增加 Actuator，并新增强制读取环境变量的生产配置。
3. 新增后端多阶段 Dockerfile，使用非 root 用户运行。
4. 新增前端多阶段 Dockerfile和 Caddy 配置，实现静态站点、SPA 回退、HTTPS 与 API/SSE 反向代理。
5. 新增 PostgreSQL/PGVector、Kafka KRaft、MinIO、后端和前端的生产 Compose 编排。
6. 新增生产环境变量模板、数据库与对象存储备份脚本。
7. 编写中文上线、验证、备份、升级和回滚说明。
8. 运行前后端测试、构建、Compose 解析、脚本语法和差异检查，最后合并为一个提交。
