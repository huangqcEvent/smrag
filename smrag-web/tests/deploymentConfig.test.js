import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import path from 'node:path'

const repositoryRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..')

const readRepositoryFile = (relativePath) =>
  readFileSync(path.join(repositoryRoot, relativePath), 'utf8')

test('production compose keeps stateful services private and persistent', () => {
  const compose = readRepositoryFile('compose.prod.yml')

  for (const service of [
    'postgres',
    'kafka-permissions',
    'kafka',
    'minio',
    'backend',
    'frontend',
  ]) {
    assert.match(compose, new RegExp(`^  ${service}:`, 'm'))
  }

  assert.match(compose, /pgvector\/pgvector:0\.8\.2-pg17/)
  assert.match(compose, /apache\/kafka:3\.9\.2/)
  assert.match(compose, /quay\.io\/minio\/minio:RELEASE\./)
  assert.match(compose, /postgres_data:/)
  assert.match(compose, /kafka_data:/)
  assert.match(compose, /minio_data:/)
  assert.match(compose, /caddy_data:/)
  assert.match(compose, /kafka_data:\/tmp\/kraft-combined-logs/)

  assert.doesNotMatch(compose, /(?:5432|9092|9000|9001):(?:5432|9092|9000|9001)/)
  assert.match(compose, /- "80:80"/)
  assert.match(compose, /- "443:443"/)
  assert.match(compose, /KAFKA_HEAP_OPTS: "-Xms512m -Xmx768m"/)
  assert.equal([...compose.matchAll(/^    mem_limit:/gm)].length, 7)
})

test('production secrets are injected and unsafe defaults are absent', () => {
  const compose = readRepositoryFile('compose.prod.yml')
  const productionConfig = readRepositoryFile(
    'smrag-core/src/main/resources/application-prod.yml',
  )
  const environmentExample = readRepositoryFile('.env.prod.example')

  assert.match(compose, /env_file:\s*\n\s*- \.env\.prod/)
  assert.match(productionConfig, /\$\{SPRING_DATASOURCE_PASSWORD\}/)
  assert.match(productionConfig, /\$\{SMRAG_STORAGE_MINIO_SECRET_KEY\}/)
  assert.match(productionConfig, /\$\{SMRAG_SECURITY_JWT_SECRET_KEY\}/)
  assert.match(productionConfig, /\$\{DASHSCOPE_API_KEY\}/)
  assert.doesNotMatch(environmentExample, /=postgres\s*$/m)
  assert.doesNotMatch(environmentExample, /=minioadmin\s*$/m)
  for (const secretName of [
    'POSTGRES_PASSWORD',
    'MINIO_ROOT_PASSWORD',
    'KAFKA_CLUSTER_ID',
    'SMRAG_SECURITY_JWT_SECRET_KEY',
    'SMRAG_BOOTSTRAP_ADMIN_PASSWORD',
    'DASHSCOPE_API_KEY',
  ]) {
    assert.match(compose, new RegExp(`\\$\\{${secretName}:\\?`))
    assert.match(environmentExample, new RegExp(`^${secretName}=$`, 'm'))
  }
  assert.match(environmentExample, /^BACKEND_JAVA_TOOL_OPTIONS="[^"]+"$/m)
})

test('gateway supports SPA routes, secure headers and unbuffered API streaming', () => {
  const caddyfile = readRepositoryFile('smrag-web/Caddyfile')

  assert.match(caddyfile, /handle \/api\/\*/)
  assert.match(caddyfile, /reverse_proxy backend:8080/)
  assert.match(caddyfile, /flush_interval -1/)
  assert.match(caddyfile, /try_files \{path\} \/index\.html/)
  assert.match(caddyfile, /X-Content-Type-Options "nosniff"/)
  const compose = readRepositoryFile('compose.prod.yml')
  assert.match(compose, /frontend:[\s\S]*healthcheck:/)
  assert.match(compose, /http:\/\/127\.0\.0\.1:2019\/config\//)
  assert.doesNotMatch(compose, /http:\/\/localhost\/$/m)
})

test('images use multi-stage builds and backend exposes a safe health endpoint', () => {
  const backendDockerfile = readRepositoryFile('smrag-core/Dockerfile')
  const frontendDockerfile = readRepositoryFile('smrag-web/Dockerfile')
  const securityConfig = readRepositoryFile(
    'smrag-core/src/main/java/com/hqc/smrag/security/config/SecurityConfig.java',
  )
  const productionConfig = readRepositoryFile(
    'smrag-core/src/main/resources/application-prod.yml',
  )
  const passwordLockMigration = readRepositoryFile(
    'smrag-core/src/main/resources/db/migration/V11__lock_seeded_admin_password.sql',
  )

  assert.match(backendDockerfile, /^FROM .* AS build$/m)
  assert.match(backendDockerfile, /^USER fundrag$/m)
  assert.match(frontendDockerfile, /^FROM .* AS build$/m)
  assert.match(frontendDockerfile, /^FROM caddy:2\.10\.2-alpine$/m)
  assert.match(securityConfig, /"\/actuator\/health"/)
  assert.match(productionConfig, /log-impl: org\.apache\.ibatis\.logging\.nologging\.NoLoggingImpl/)
  assert.match(productionConfig, /\$\{SMRAG_BOOTSTRAP_ADMIN_PASSWORD\}/)
  assert.match(passwordLockMigration, /BOOTSTRAP_ADMIN_PASSWORD_REQUIRED/)
})

test('backup covers both relational data and original document objects', () => {
  const backupScript = readRepositoryFile('scripts/backup.sh')
  const restoreScript = readRepositoryFile('scripts/restore.sh')
  const deploymentGuide = readRepositoryFile('docs/deployment.md')

  assert.match(backupScript, /umask 077/)
  assert.match(backupScript, /stop .*backend/)
  assert.match(backupScript, /pg_dump/)
  assert.match(backupScript, /PGPASSWORD/)
  assert.match(backupScript, /mc mirror/)
  assert.match(backupScript, /BACKUP_RETENTION_DAYS/)
  assert.match(backupScript, /\.complete/)
  assert.match(backupScript, /\[0-9\]\{8\}T\[0-9\]\{6\}Z/)
  assert.match(restoreScript, /RESTORE_CONFIRM/)
  assert.match(restoreScript, /pg_restore/)
  assert.match(restoreScript, /mc mirror/)
  assert.match(restoreScript, /status = 'PENDING'/)
  assert.match(deploymentGuide, /compose .* exec .* backend .*actuator\/health/s)
})

test('deployment scripts keep Unix line endings on every checkout', () => {
  const attributes = readRepositoryFile('.gitattributes')

  assert.match(attributes, /^\*\.sh text eol=lf$/m)
})
