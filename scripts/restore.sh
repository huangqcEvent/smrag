#!/usr/bin/env bash

set -Eeuo pipefail
umask 077

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
project_dir="$(cd -- "${script_dir}/.." && pwd)"
env_file="${ENV_FILE:-${project_dir}/.env.prod}"
requested_backup_dir="${1:-}"

if [[ "${RESTORE_CONFIRM:-}" != "RESTORE_FUNDRAG" ]]; then
  echo "恢复会覆盖当前数据库和文档。请设置 RESTORE_CONFIRM=RESTORE_FUNDRAG 后重试。" >&2
  exit 1
fi

if [[ -z "${requested_backup_dir}" || ! -d "${requested_backup_dir}" ]]; then
  echo "用法：RESTORE_CONFIRM=RESTORE_FUNDRAG $0 <备份目录>" >&2
  exit 1
fi

backup_dir="$(cd -- "${requested_backup_dir}" && pwd -P)"
if [[ "${backup_dir}" == "/" ]]; then
  echo "拒绝从不安全的目录恢复：${backup_dir}" >&2
  exit 1
fi

for required_path in \
  "${backup_dir}/.complete" \
  "${backup_dir}/SHA256SUMS" \
  "${backup_dir}/postgres.dump" \
  "${backup_dir}/minio"; do
  if [[ ! -e "${required_path}" ]]; then
    echo "备份不完整，缺少：${required_path}" >&2
    exit 1
  fi
done

if [[ ! -f "${env_file}" ]]; then
  echo "未找到生产环境文件：${env_file}" >&2
  exit 1
fi

echo "正在校验备份完整性..."
(
  cd "${backup_dir}"
  sha256sum --check SHA256SUMS
)

set -a
# shellcheck disable=SC1090
source "${env_file}"
set +a

required_variables=(
  POSTGRES_DB
  POSTGRES_USER
  POSTGRES_PASSWORD
  MINIO_ROOT_USER
  MINIO_ROOT_PASSWORD
)

for variable_name in "${required_variables[@]}"; do
  if [[ -z "${!variable_name:-}" ]]; then
    echo "环境变量 ${variable_name} 未设置" >&2
    exit 1
  fi
done

bucket_name="${SMRAG_STORAGE_MINIO_BUCKET_NAME:-fundrag-document}"
compose_command=(
  docker compose
  --env-file "${env_file}"
  -f "${project_dir}/compose.prod.yml"
)

echo "正在停止对外服务..."
"${compose_command[@]}" stop --timeout 60 frontend backend

echo "正在启动恢复所需的基础设施..."
"${compose_command[@]}" up -d --wait postgres minio

echo "正在恢复 PostgreSQL..."
"${compose_command[@]}" exec -T \
  --env "PGPASSWORD=${POSTGRES_PASSWORD}" \
  postgres \
  pg_restore \
  --username "${POSTGRES_USER}" \
  --dbname "${POSTGRES_DB}" \
  --clean \
  --if-exists \
  --no-owner \
  --no-privileges \
  --exit-on-error < "${backup_dir}/postgres.dump"

echo "正在恢复 MinIO 文档对象..."
docker run --rm \
  --network fundrag_app \
  --user "$(id -u):$(id -g)" \
  --volume "${backup_dir}/minio:/backup:ro" \
  --env MINIO_ROOT_USER \
  --env MINIO_ROOT_PASSWORD \
  --env "BUCKET_NAME=${bucket_name}" \
  --env "MC_CONFIG_DIR=/tmp/.mc" \
  --entrypoint /bin/sh \
  quay.io/minio/mc:RELEASE.2025-04-16T18-13-26Z \
  -ec '
    mc alias set target http://minio:9000 "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD"
    mc mb --ignore-existing "target/$BUCKET_NAME"
    mc mirror --overwrite --remove /backup "target/$BUCKET_NAME"
  '

echo "正在清空消息队列并恢复未完成的解析任务..."
"${compose_command[@]}" rm --force --stop kafka kafka-init kafka-permissions
if docker volume inspect fundrag_kafka_data >/dev/null 2>&1; then
  docker volume rm fundrag_kafka_data >/dev/null
fi
"${compose_command[@]}" up -d --wait kafka
"${compose_command[@]}" run --rm kafka-init

"${compose_command[@]}" exec -T \
  --env "PGPASSWORD=${POSTGRES_PASSWORD}" \
  postgres \
  psql \
  --username "${POSTGRES_USER}" \
  --dbname "${POSTGRES_DB}" <<'SQL'
BEGIN;

UPDATE knowledge_document
SET status = 'PENDING',
    processing_started_at = NULL,
    failure_reason = NULL,
    update_time = CURRENT_TIMESTAMP
WHERE status = 'PROCESSING';

UPDATE document_ingestion_outbox outbox
SET status = 'PENDING',
    attempt_count = 0,
    next_attempt_at = CURRENT_TIMESTAMP,
    last_error = NULL,
    published_at = NULL
WHERE outbox.status = 'PUBLISHED'
  AND EXISTS (
      SELECT 1
      FROM knowledge_document document
      WHERE document.id = outbox.document_id
        AND document.status = 'PENDING'
  );

COMMIT;
SQL

echo "正在启动 FundRAG..."
"${compose_command[@]}" up -d backend frontend
"${compose_command[@]}" ps

echo "恢复完成。请立即执行文档上传、问答和引用预览验收。"
