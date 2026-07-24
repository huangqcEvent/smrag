# FundRAG 云端部署

这套配置用于把 FundRAG 部署到一台 Ubuntu 24.04 云服务器。Caddy 对外提供网页、API 反向代理和自动 HTTPS；PostgreSQL、Kafka、MinIO、后端均不直接暴露到公网。

## 1. 准备服务器

推荐配置：

- 首次演示：4 核 8 GB、80 GB SSD。
- 更稳定的面试和多人体验：8 核 16 GB。
- 安全组开放 80、443；22 端口只允许你的固定 IP。
- 若选择中国大陆服务器并绑定域名，需要先完成 ICP 备案。希望尽快上线时可选择中国香港节点。

按照 [Docker 官方 Ubuntu 安装文档](https://docs.docker.com/engine/install/ubuntu/) 安装 Docker Engine 和 Compose 插件，然后确认：

```bash
docker --version
docker compose version
```

## 2. 准备生产配置

在服务器克隆项目并进入仓库根目录：

```bash
cp .env.prod.example .env.prod
chmod 600 .env.prod
```

生成 Kafka 集群 ID 和随机密钥：

```bash
docker run --rm apache/kafka:3.9.2 \
  /opt/kafka/bin/kafka-storage.sh random-uuid
openssl rand -base64 48
openssl rand -base64 36
```

编辑 `.env.prod`：

- `DOMAIN`：有域名时填写 `rag.example.com`；仅使用服务器 IP 验证时填写 `:80`。
- `KAFKA_CLUSTER_ID`：填写上面第一条命令的输出。
- `POSTGRES_PASSWORD`、`MINIO_ROOT_PASSWORD`、`SMRAG_SECURITY_JWT_SECRET_KEY`：分别使用随机值，不能留空。
- `SMRAG_BOOTSTRAP_ADMIN_PASSWORD`：首次启动的管理员密码，至少 12 位；系统初始化后可在用户管理中修改，后续启动不会被环境变量覆盖。
- `DASHSCOPE_API_KEY`：填写真实的阿里云百炼 API Key。

域名部署时，在 DNS 中添加一条 A 记录指向服务器公网 IP。80 和 443 必须能从公网访问，Caddy 才能自动签发证书。

## 3. 启动

先检查 Compose 渲染结果，再构建并启动：

```bash
docker compose --env-file .env.prod -f compose.prod.yml config --quiet
docker compose --env-file .env.prod -f compose.prod.yml up -d --build
docker compose --env-file .env.prod -f compose.prod.yml ps
```

首次构建需要下载 Maven、npm 和 Docker 依赖，时间会比后续升级长。后端会自动执行 Flyway 数据库迁移、创建 MinIO bucket；`kafka-init` 会创建文档解析和死信主题。

Compose 已按 8 GB 服务器设置容器内存上限，避免 Java 或 Kafka 抢占全部宿主机内存。服务器内存更大时可以按实际压测结果调整 `mem_limit` 和 JVM 参数。

查看启动日志：

```bash
docker compose --env-file .env.prod -f compose.prod.yml logs -f backend frontend
```

浏览器访问 `https://你的域名`；IP 验证模式访问 `http://服务器IP`。

## 4. 上线验收

按顺序验证：

1. 打开登录页并登录管理员账户。
2. 创建角色、用户和知识库，检查角色授权。
3. 上传 PDF 或 Word 文档，等待状态变为“已完成”。
4. 发起问答，确认答案可以流式输出。
5. 点击答案引用，确认原文件阅读器可打开。
6. 换成无权限账户，确认可以看见文档条目，但不能阅读内容或被 RAG 检索引用。

容器健康状态可通过以下命令确认：

```bash
docker compose --env-file .env.prod -f compose.prod.yml ps
docker compose --env-file .env.prod -f compose.prod.yml exec -T \
  backend curl --fail --silent http://localhost:8080/actuator/health
```

第二条命令从容器内部访问后端健康检查；正常响应为 `{"status":"UP"}`。健康检查不经过公网网关。

## 5. 备份

备份脚本会短暂停止后端写入，同时保存 PostgreSQL 数据和 MinIO 原始文档，并生成 SHA-256 完整性清单和完成标记。默认保留 7 天：

```bash
chmod +x scripts/backup.sh
./scripts/backup.sh
```

默认输出到仓库的 `backups/时间戳/`。生产环境建议把备份写入另一块磁盘或对象存储：

```bash
BACKUP_DIR=/data/fundrag-backups ./scripts/backup.sh
```

每天凌晨 3 点执行的 crontab 示例：

```cron
0 3 * * * cd /opt/fundrag && BACKUP_DIR=/data/fundrag-backups ./scripts/backup.sh >> /var/log/fundrag-backup.log 2>&1
```

恢复会覆盖当前数据库和 MinIO bucket，必须显式确认：

```bash
RESTORE_CONFIRM=RESTORE_FUNDRAG \
  ./scripts/restore.sh /data/fundrag-backups/20260724T030000Z
```

恢复脚本会先验证校验和，停止前后端，恢复 PostgreSQL 与 MinIO，清空无法与快照保持一致的 Kafka topic，并把快照中未完成的 outbox 任务安全重置为 `PENDING` 后重新投递。恢复失败时不会自动启动业务服务，先排查错误再重试。

只有备份不够，至少执行一次恢复演练，并完成登录、上传、问答和引用预览验收。

## 6. 升级与回滚

升级前先运行备份，再拉取指定版本并重建：

```bash
./scripts/backup.sh
git fetch --tags
git checkout <已验证的版本标签>
docker compose --env-file .env.prod -f compose.prod.yml up -d --build
```

查看异常：

```bash
docker compose --env-file .env.prod -f compose.prod.yml ps
docker compose --env-file .env.prod -f compose.prod.yml logs --tail=200 backend
```

需要回滚时，切回上一个已验证标签并再次执行 `up -d --build`。Flyway 数据库迁移默认只向前执行；涉及破坏性表结构的版本必须单独准备回滚脚本。

## 7. 当前方案边界

这是单机起步方案，任一宿主机故障都会造成整体不可用。出现以下情况时应升级架构：

- 文档量和向量规模持续增长：迁移到独立或托管 PostgreSQL/向量数据库。
- 上传和解析任务显著增加：使用托管 Kafka，并增加解析消费者实例。
- 文件容量增长：迁移到云厂商对象存储。
- 需要高可用：后端多实例、负载均衡、托管数据库和跨可用区存储。
