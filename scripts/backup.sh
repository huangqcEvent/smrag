#!/usr/bin/env bash

set -Eeuo pipefail
umask 077

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
project_dir="$(cd -- "${script_dir}/.." && pwd)"
env_file="${ENV_FILE:-${project_dir}/.env.prod}"
backup_root="${BACKUP_DIR:-${project_dir}/backups}"

if [[ ! -f "${env_file}" ]]; then
  echo "未找到生产环境文件：${env_file}" >&2
  exit 1
fi

if [[ -z "${backup_root}" || "${backup_root}" == "/" ]]; then
  echo "拒绝使用不安全的备份目录：${backup_root}" >&2
  exit 1
fi

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
retention_days="${BACKUP_RETENTION_DAYS:-7}"
timestamp="$(date -u +'%Y%m%dT%H%M%SZ')"
backup_dir="${backup_root}/${timestamp}"
backup_succeeded=false
backend_was_running=false

mkdir -p "${backup_dir}/minio"

compose_command=(
  docker compose
  --env-file "${env_file}"
  -f "${project_dir}/compose.prod.yml"
)

if "${compose_command[@]}" ps --status running --services | grep -qx backend; then
  backend_was_running=true
fi

finish_backup() {
  exit_code=$?
  trap - EXIT

  if [[ "${backend_was_running}" == true ]]; then
    echo "正在恢复后端服务..."
    if ! "${compose_command[@]}" start backend >/dev/null; then
      echo "后端服务恢复失败，请立即检查 docker compose ps 和日志" >&2
      exit_code=1
    fi
  fi

  if [[ "${backup_succeeded}" != true ]]; then
    rm -rf -- "${backup_dir}"
  fi

  exit "${exit_code}"
}
trap finish_backup EXIT

if [[ "${backend_was_running}" == true ]]; then
  echo "正在暂停后端写入以获得一致性备份..."
  "${compose_command[@]}" stop --timeout 60 backend
fi

echo "正在备份 PostgreSQL..."
"${compose_command[@]}" exec -T \
  --env "PGPASSWORD=${POSTGRES_PASSWORD}" \
  postgres \
  pg_dump \
  --username "${POSTGRES_USER}" \
  --dbname "${POSTGRES_DB}" \
  --format custom \
  --file /dev/stdout > "${backup_dir}/postgres.dump"

echo "正在备份 MinIO 文档对象..."
docker run --rm \
  --network fundrag_app \
  --user "$(id -u):$(id -g)" \
  --volume "${backup_dir}/minio:/backup" \
  --env MINIO_ROOT_USER \
  --env MINIO_ROOT_PASSWORD \
  --env "BUCKET_NAME=${bucket_name}" \
  --env "MC_CONFIG_DIR=/tmp/.mc" \
  --entrypoint /bin/sh \
  quay.io/minio/mc:RELEASE.2025-04-16T18-13-26Z \
  -ec '
    mc alias set source http://minio:9000 "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD"
    mc mirror --overwrite "source/$BUCKET_NAME" /backup
  '

echo "正在生成完整性校验文件..."
(
  cd "${backup_dir}"
  sha256sum postgres.dump
  find minio -type f -print0 | sort -z | xargs -0 -r sha256sum
) > "${backup_dir}/SHA256SUMS"
touch "${backup_dir}/.complete"
backup_succeeded=true

echo "正在清理 ${retention_days} 天前的备份..."
find "${backup_root}" \
  -mindepth 1 \
  -maxdepth 1 \
  -type d \
  -regextype posix-extended \
  -regex '.*/[0-9]{8}T[0-9]{6}Z' \
  -mtime "+${retention_days}" \
  -exec rm -rf -- {} +

echo "备份完成：${backup_dir}"
