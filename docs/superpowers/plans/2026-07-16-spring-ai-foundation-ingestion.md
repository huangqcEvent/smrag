# Spring AI 基础与文档入库实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 在 JDK 17 目标字节码下完成 Spring AI 正式版升级、数据库迁移、Spring AI ETL 入库、可靠 Kafka 消费和完整向量删除。

**架构：** 通过三个 BOM 对齐 Spring Boot、Spring AI 和 DashScope；Flyway 管理关系表、Spring AI Chat Memory 表与全新的 `rag_vector_store`；Kafka 消费者只负责委托，入库服务负责 Reader、Splitter、关系数据和向量存储的一致性补偿。

**技术栈：** JDK 17、Spring Boot 3.5.8、Spring AI 1.1.2、Spring AI Alibaba 1.1.2.0、PostgreSQL/PGVector、Flyway、Kafka、MyBatis Plus、JUnit 5、Mockito、Testcontainers。

**前置条件：** 无；这是三份计划中的第一阶段。

---

## 文件结构

- 修改：`pom.xml`，统一 release 版本与 BOM。
- 修改：`smrag-core/pom.xml`，替换旧 starter 并加入 RAG、Reader、Memory、Flyway 和测试依赖。
- 修改：`smrag-core/src/main/resources/application.yml`，加入 PGVector、Flyway、Kafka 重试和入库参数。
- 创建：`smrag-core/src/main/resources/db/migration/V1__baseline_schema.sql`，建立当前业务表。
- 创建：`smrag-core/src/main/resources/db/migration/V2__spring_ai_rag_schema.sql`，建立 UUID 向量表、Chat Memory 表和新增字段。
- 创建：`smrag-core/src/main/java/com/hqc/smrag/config/RagProperties.java`，承载检索与切片参数。
- 创建：`smrag-core/src/main/java/com/hqc/smrag/config/KafkaRetryConfig.java`，配置重试和死信恢复。
- 创建：`smrag-core/src/main/java/com/hqc/smrag/service/knowledge/VectorDocumentIdGenerator.java`，生成稳定 UUID。
- 创建：`smrag-core/src/main/java/com/hqc/smrag/service/knowledge/SpringAiDocumentReader.java`，选择 PDF/Tika Reader。
- 创建：`smrag-core/src/main/java/com/hqc/smrag/service/knowledge/DocumentChunker.java`，用 TokenTextSplitter 切片并补齐 metadata。
- 创建：`smrag-core/src/main/java/com/hqc/smrag/service/knowledge/DocumentIngestionService.java`，完成幂等入库和失败补偿。
- 创建：`smrag-core/src/main/java/com/hqc/smrag/service/knowledge/DocumentIngestionPersistenceService.java`，隔离关系事务和失败状态事务。
- 创建：`smrag-core/src/main/java/com/hqc/smrag/kafka/DocumentParseRecoverer.java`，写入死信并落失败状态。
- 修改：`smrag-core/src/main/java/com/hqc/smrag/kafka/DocumentParseConsumer.java`，删除吞异常逻辑。
- 修改：`smrag-core/src/main/java/com/hqc/smrag/service/knowledge/DocumentService.java`，加入校验值、版本和完整删除。
- 修改：`smrag-core/src/main/java/com/hqc/smrag/entity/KnowledgeDocument.java`、`smrag-core/src/main/java/com/hqc/smrag/entity/KnowledgeChunk.java`、`smrag-core/src/main/java/com/hqc/smrag/dto/DocumentParseTaskDTO.java`，加入版本化入库字段。
- 创建：各任务文件清单列出的单元测试与 `smrag-core/src/test/java/com/hqc/smrag/integration/RagSchemaMigrationTest.java`。

工作区已有未提交改动。开始每个任务前先保存 `git status --short` 和相关文件的 `git diff`；只暂存本任务新增的 hunks，不使用 `git add .`。若列出的文件在任务前已有无关修改，使用 IDE selective staging 分离 hunks；无法可靠分离时跳过该任务提交并在验收报告中列出，不能把既有修改一起提交。

### 任务 1：升级并锁定 Spring AI 正式版依赖

**文件：**
- 修改：`pom.xml`
- 修改：`smrag-core/pom.xml`
- 修改：`smrag-core/src/main/java/com/hqc/smrag/service/knowledge/VectorService.java`
- 修改：`smrag-core/src/main/java/com/hqc/smrag/service/chat/RagService.java`
- 创建：`smrag-core/src/test/java/com/hqc/smrag/config/SpringAiDependencySmokeTest.java`

- [ ] **步骤 1：编写会因旧 M2 API 缺失而失败的依赖烟雾测试**

```java
package com.hqc.smrag.config;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.tool.annotation.Tool;

import static org.assertj.core.api.Assertions.assertThat;

class SpringAiDependencySmokeTest {

    @Test
    void exposesRequiredSpringAiReleaseApis() {
        assertThat(MessageChatMemoryAdvisor.class).isNotNull();
        assertThat(RetrievalAugmentationAdvisor.class).isNotNull();
        assertThat(TikaDocumentReader.class).isNotNull();
        assertThat(Tool.class).isNotNull();
    }
}
```

- [ ] **步骤 2：运行测试并确认旧依赖无法编译测试**

运行：

```powershell
$env:JAVA_HOME='D:\JavaProject\development\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
mvn -pl smrag-core -Dtest=SpringAiDependencySmokeTest test
```

预期：FAIL，编译器报告 `org.springframework.ai.rag`、`reader.tika` 或 `tool.annotation` 不存在。

- [ ] **步骤 3：在根 POM 中锁定兼容版本并移除 milestone/snapshot 仓库**

将 properties 和 dependency management 调整为：

```xml
<properties>
    <maven.compiler.release>17</maven.compiler.release>
    <maven.compiler.parameters>true</maven.compiler.parameters>
    <spring-boot.version>3.5.8</spring-boot.version>
    <spring-ai.version>1.1.2</spring-ai.version>
    <spring-ai-alibaba.version>1.1.2.0</spring-ai-alibaba.version>
    <spring-ai-alibaba-extensions.version>1.1.2.1</spring-ai-alibaba-extensions.version>
    <mybatis-plus.version>3.5.7</mybatis-plus.version>
    <hutool.version>5.8.25</hutool.version>
    <lombok.version>1.18.36</lombok.version>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
</properties>

<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-dependencies</artifactId>
            <version>${spring-boot.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
        <dependency>
            <groupId>com.alibaba.cloud.ai</groupId>
            <artifactId>spring-ai-alibaba-bom</artifactId>
            <version>${spring-ai-alibaba.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-bom</artifactId>
            <version>${spring-ai.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
        <dependency>
            <groupId>com.alibaba.cloud.ai</groupId>
            <artifactId>spring-ai-alibaba-extensions-bom</artifactId>
            <version>${spring-ai-alibaba-extensions.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

删除根 POM 中的 `<repositories>` 和 `<pluginRepositories>`。

- [ ] **步骤 4：替换核心模块依赖**

保留现有 Web、Kafka、MyBatis、PostgreSQL、Hutool、Lombok 和测试依赖；移除旧 Alibaba starter、旧 PGVector 依赖和手工 Tika 依赖，并加入：

```xml
<dependency>
    <groupId>com.alibaba.cloud.ai</groupId>
    <artifactId>spring-ai-alibaba-starter-dashscope</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-vector-store-pgvector</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-rag</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-tika-document-reader</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-pdf-document-reader</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-chat-memory-repository-jdbc</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
</dependency>
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-database-postgresql</artifactId>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>postgresql</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>kafka</artifactId>
    <scope>test</scope>
</dependency>
```

- [ ] **步骤 5：运行烟雾测试和现有单元测试**

运行：

```powershell
$env:JAVA_HOME='D:\JavaProject\development\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
mvn -pl smrag-core test
```

预期：首次运行会暴露两个已知正式版 API 差异。把 `RagService` 的 `Document.getContent()` 改为 `Document.getText()`；把 `VectorService` 的 SearchRequest 改为：

```java
SearchRequest request = SearchRequest.builder()
    .query(query)
    .topK(topK)
    .build();
```

不在本步骤重构聊天或检索行为。完成这两个机械兼容点后重新运行，预期 PASS。

- [ ] **步骤 6：提交依赖升级**

```powershell
git add -- pom.xml smrag-core/pom.xml smrag-core/src/main/java/com/hqc/smrag/service/knowledge/VectorService.java smrag-core/src/main/java/com/hqc/smrag/service/chat/RagService.java smrag-core/src/test/java/com/hqc/smrag/config/SpringAiDependencySmokeTest.java
git commit -m "build: align Spring AI release dependencies"
```

### 任务 2：用 Flyway 建立兼容的 RAG 数据结构

**文件：**
- 创建：`smrag-core/src/main/resources/db/migration/V1__baseline_schema.sql`
- 创建：`smrag-core/src/main/resources/db/migration/V2__spring_ai_rag_schema.sql`
- 修改：`smrag-core/src/main/resources/application.yml`
- 创建：`smrag-core/src/test/java/com/hqc/smrag/integration/RagSchemaMigrationTest.java`

- [ ] **步骤 1：编写迁移集成测试**

```java
package com.hqc.smrag.integration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.ResultSet;

import static org.assertj.core.api.Assertions.assertThat;

class RagSchemaMigrationTest {

    @Test
    void createsUuidVectorStoreAndIngestionColumns() throws Exception {
        DockerImageName image = DockerImageName.parse("pgvector/pgvector:pg16")
            .asCompatibleSubstituteFor("postgres");
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(image)) {
            postgres.start();
            Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .load()
                .migrate();

            try (Connection connection = postgres.createConnection("");
                 ResultSet columns = connection.getMetaData()
                     .getColumns(null, "public", "knowledge_chunk", "vector_id")) {
                assertThat(columns.next()).isTrue();
                assertThat(columns.getString("TYPE_NAME")).isEqualTo("uuid");
            }
            try (Connection connection = postgres.createConnection("");
                 ResultSet tables = connection.getMetaData()
                     .getTables(null, "public", "rag_vector_store", new String[] {"TABLE"})) {
                assertThat(tables.next()).isTrue();
            }
        }
    }
}
```

- [ ] **步骤 2：运行并确认缺少迁移文件而失败**

运行：

```powershell
mvn -pl smrag-core -Dtest=RagSchemaMigrationTest test
```

预期：FAIL，`knowledge_chunk.vector_id` 或 `rag_vector_store` 不存在。

- [ ] **步骤 3：创建 V1 基线迁移**

V1 只建立当前六张业务表和旧向量索引，不插入 `sql/init.sql` 中的演示数据：

```sql
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS knowledge_document (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255),
    file_path VARCHAR(500),
    file_type VARCHAR(50),
    status VARCHAR(20) DEFAULT 'PENDING',
    chunk_count INT DEFAULT 0,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS knowledge_chunk (
    id BIGSERIAL PRIMARY KEY,
    document_id BIGINT,
    content TEXT,
    chunk_index INT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS fund_product (
    id BIGSERIAL PRIMARY KEY,
    product_code VARCHAR(100),
    product_name VARCHAR(255),
    manager_id BIGINT,
    risk_level VARCHAR(20),
    return_rate DECIMAL(10,4),
    investment_strategy TEXT,
    min_amount DECIMAL(20,2),
    status VARCHAR(20) DEFAULT 'ACTIVE',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS fund_manager (
    id BIGSERIAL PRIMARY KEY,
    manager_code VARCHAR(100),
    manager_name VARCHAR(255),
    company_name VARCHAR(255),
    register_time TIMESTAMP,
    assets_scale DECIMAL(20,2),
    qualification TEXT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS chat_session (
    id BIGSERIAL PRIMARY KEY,
    session_id VARCHAR(100),
    user_id BIGINT,
    question TEXT,
    rewrite_question TEXT,
    answer TEXT,
    route_type VARCHAR(50),
    reference_docs TEXT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS vector_store (
    id BIGSERIAL PRIMARY KEY,
    content TEXT,
    embedding VECTOR(1536),
    metadata JSONB,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS vector_embedding_idx
    ON vector_store USING ivfflat (embedding vector_cosine_ops);
```

- [ ] **步骤 4：创建 V2 RAG 迁移**

```sql
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS hstore;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

ALTER TABLE knowledge_document
    ADD COLUMN IF NOT EXISTS knowledge_base_id VARCHAR(64) NOT NULL DEFAULT 'default',
    ADD COLUMN IF NOT EXISTS checksum VARCHAR(64),
    ADD COLUMN IF NOT EXISTS version INT NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS failure_reason TEXT;

ALTER TABLE knowledge_chunk
    ADD COLUMN IF NOT EXISTS vector_id UUID,
    ADD COLUMN IF NOT EXISTS page_number INT,
    ADD COLUMN IF NOT EXISTS document_version INT NOT NULL DEFAULT 1;

CREATE UNIQUE INDEX IF NOT EXISTS uk_knowledge_chunk_vector_id
    ON knowledge_chunk(vector_id) WHERE vector_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS rag_vector_store (
    id UUID DEFAULT uuid_generate_v4() PRIMARY KEY,
    content TEXT,
    metadata JSON,
    embedding VECTOR(1536)
);

CREATE INDEX IF NOT EXISTS rag_vector_store_embedding_hnsw_idx
    ON rag_vector_store USING HNSW (embedding vector_cosine_ops);

CREATE TABLE IF NOT EXISTS SPRING_AI_CHAT_MEMORY (
    conversation_id VARCHAR(36) NOT NULL,
    content TEXT NOT NULL,
    type VARCHAR(10) NOT NULL CHECK (type IN ('USER', 'ASSISTANT', 'SYSTEM', 'TOOL')),
    "timestamp" TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS SPRING_AI_CHAT_MEMORY_CONVERSATION_ID_TIMESTAMP_IDX
    ON SPRING_AI_CHAT_MEMORY(conversation_id, "timestamp");
```

- [ ] **步骤 5：启用显式迁移和 PGVector 校验**

在 `application.yml` 中加入：

```yaml
spring:
  flyway:
    enabled: true
    baseline-on-migrate: true
    baseline-version: 0
  ai:
    chat:
      memory:
        repository:
          jdbc:
            initialize-schema: never
    vectorstore:
      pgvector:
        table-name: rag_vector_store
        dimensions: 1536
        index-type: HNSW
        distance-type: COSINE_DISTANCE
        initialize-schema: false
        schema-validation: true
```

- [ ] **步骤 6：重新运行迁移测试**

运行：`mvn -pl smrag-core -Dtest=RagSchemaMigrationTest test`

预期：PASS。

- [ ] **步骤 7：提交迁移**

```powershell
git add -- smrag-core/src/main/resources/db/migration smrag-core/src/main/resources/application.yml smrag-core/src/test/java/com/hqc/smrag/integration/RagSchemaMigrationTest.java
git commit -m "feat: add managed RAG database schema"
```

### 任务 3：引入入库配置与稳定向量 ID

**文件：**
- 创建：`smrag-core/src/main/java/com/hqc/smrag/config/RagProperties.java`
- 创建：`smrag-core/src/main/java/com/hqc/smrag/service/knowledge/VectorDocumentIdGenerator.java`
- 创建：`smrag-core/src/test/java/com/hqc/smrag/service/knowledge/VectorDocumentIdGeneratorTest.java`
- 修改：`smrag-core/src/main/resources/application.yml`

- [ ] **步骤 1：先写稳定 UUID 测试**

```java
package com.hqc.smrag.service.knowledge;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VectorDocumentIdGeneratorTest {

    @Test
    void generatesStableUuidPerDocumentVersionAndChunk() {
        VectorDocumentIdGenerator generator = new VectorDocumentIdGenerator();

        assertThat(generator.generate("default", 42L, 2, 3))
            .isEqualTo(generator.generate("default", 42L, 2, 3));
        assertThat(generator.generate("default", 42L, 2, 3))
            .isNotEqualTo(generator.generate("default", 42L, 2, 4));
    }
}
```

- [ ] **步骤 2：运行并确认类不存在**

运行：`mvn -pl smrag-core -Dtest=VectorDocumentIdGeneratorTest test`

预期：FAIL，`VectorDocumentIdGenerator` 不存在。

- [ ] **步骤 3：实现稳定 UUID**

```java
package com.hqc.smrag.service.knowledge;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Component
public class VectorDocumentIdGenerator {

    public UUID generate(String knowledgeBaseId, long documentId, int version, int chunkIndex) {
        String key = knowledgeBaseId + "/" + documentId + "/" + version + "/" + chunkIndex;
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
    }
}
```

- [ ] **步骤 4：创建配置属性并启用扫描**

`RagProperties` 使用 `@ConfigurationProperties(prefix = "smrag.ai.rag")`，字段及默认值为 `topK=8`、`similarityThreshold=0.65`、`maxContextDocuments=5`、`memoryWindow=12`、`chunkSize=800`、`minChunkSizeChars=350`、`minChunkLengthToEmbed=20`、`maxNumChunks=5000`、`maxUploadSizeBytes=20971520`。在 `SmragApplication` 上加入 `@ConfigurationPropertiesScan`。

- [ ] **步骤 5：写入应用配置并运行测试**

```yaml
smrag:
  ai:
    rag:
      top-k: 8
      similarity-threshold: 0.65
      max-context-documents: 5
      memory-window: 12
      chunk-size: 800
      min-chunk-size-chars: 350
      min-chunk-length-to-embed: 20
      max-num-chunks: 5000
      max-upload-size-bytes: 20971520
      reindex-on-startup: false
```

运行：`mvn -pl smrag-core -Dtest=VectorDocumentIdGeneratorTest test`

预期：PASS。

- [ ] **步骤 6：提交配置与 ID 生成器**

```powershell
git add -- smrag-core/src/main/java/com/hqc/smrag/config/RagProperties.java smrag-core/src/main/java/com/hqc/smrag/SmragApplication.java smrag-core/src/main/java/com/hqc/smrag/service/knowledge/VectorDocumentIdGenerator.java smrag-core/src/main/resources/application.yml smrag-core/src/test/java/com/hqc/smrag/service/knowledge/VectorDocumentIdGeneratorTest.java
git commit -m "feat: add configurable RAG ingestion settings"
```

### 任务 4：使用 Spring AI Reader 与 TokenTextSplitter

**文件：**
- 创建：`smrag-core/src/main/java/com/hqc/smrag/service/knowledge/SpringAiDocumentReader.java`
- 创建：`smrag-core/src/main/java/com/hqc/smrag/service/knowledge/DocumentChunker.java`
- 创建：`smrag-core/src/test/java/com/hqc/smrag/service/knowledge/DocumentChunkerTest.java`

- [ ] **步骤 1：编写 metadata 与 ID 保留测试**

测试构造两个 `Document`，调用 `DocumentChunker.chunk(...)`，断言每个结果包含 `knowledgeBaseId`、`documentId`、`documentName`、`documentType`、`documentVersion`、`documentStatus`、`checksum`、`chunkIndex`，PDF 结果还包含 `pageNumber`，且 ID 能解析为 UUID。

- [ ] **步骤 2：运行并确认 `DocumentChunker` 不存在**

运行：`mvn -pl smrag-core -Dtest=DocumentChunkerTest test`

预期：FAIL，生产类不存在。

- [ ] **步骤 3：实现 Reader 选择**

```java
package com.hqc.smrag.service.knowledge;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentReader;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;

@Component
public class SpringAiDocumentReader {

    public List<Document> read(Path path, String fileType) {
        FileSystemResource resource = new FileSystemResource(path);
        DocumentReader reader = "PDF".equals(fileType)
            ? new PagePdfDocumentReader(resource)
            : new TikaDocumentReader(resource);
        return reader.get();
    }
}
```

- [ ] **步骤 4：实现切片器**

构造 `TokenTextSplitter.builder()`，从 `RagProperties` 注入五个切片参数。先给源文档合并业务 metadata，调用 splitter，再按返回顺序生成稳定 UUID，并创建 `new Document(uuid.toString(), chunk.getText(), metadata)`。PDF 的 `PagePdfDocumentReader.METADATA_START_PAGE_NUMBER` 映射为统一 `pageNumber`。

- [ ] **步骤 5：运行切片测试和现有 ChunkUtils 测试**

运行：`mvn -pl smrag-core -Dtest=DocumentChunkerTest,ChunkUtilsTest test`

预期：PASS。`ChunkUtils` 暂时保留，直到所有调用点迁移完成。

- [ ] **步骤 6：提交 Reader 与切片器**

```powershell
git add -- smrag-core/src/main/java/com/hqc/smrag/service/knowledge/SpringAiDocumentReader.java smrag-core/src/main/java/com/hqc/smrag/service/knowledge/DocumentChunker.java smrag-core/src/test/java/com/hqc/smrag/service/knowledge/DocumentChunkerTest.java
git commit -m "feat: ingest documents with Spring AI ETL"
```

### 任务 5：实现幂等入库、补偿和 Kafka 死信

**文件：**
- 修改：`smrag-core/src/main/java/com/hqc/smrag/entity/KnowledgeDocument.java`
- 修改：`smrag-core/src/main/java/com/hqc/smrag/entity/KnowledgeChunk.java`
- 修改：`smrag-core/src/main/java/com/hqc/smrag/dto/DocumentParseTaskDTO.java`
- 创建：`smrag-core/src/main/java/com/hqc/smrag/service/knowledge/DocumentIngestionService.java`
- 创建：`smrag-core/src/main/java/com/hqc/smrag/service/knowledge/DocumentIngestionPersistenceService.java`
- 创建：`smrag-core/src/main/java/com/hqc/smrag/kafka/DocumentParseRecoverer.java`
- 创建：`smrag-core/src/main/java/com/hqc/smrag/config/KafkaRetryConfig.java`
- 修改：`smrag-core/src/main/java/com/hqc/smrag/kafka/DocumentParseConsumer.java`
- 创建：`smrag-core/src/test/java/com/hqc/smrag/service/knowledge/DocumentIngestionServiceTest.java`
- 创建：`smrag-core/src/test/java/com/hqc/smrag/service/knowledge/DocumentIngestionPersistenceServiceTest.java`
- 创建：`smrag-core/src/test/java/com/hqc/smrag/kafka/DocumentParseConsumerTest.java`
- 创建：`smrag-core/src/test/java/com/hqc/smrag/kafka/DocumentParseRecovererTest.java`

- [ ] **步骤 1：编写成功入库测试**

Mock Reader 返回一个 Spring AI `Document`，Mock Chunker 返回两个带 UUID 的切片。断言 `VectorStore.add()` 只调用一次、`DocumentIngestionPersistenceService.saveCompleted` 收到两个 chunk、最终状态参数为 `COMPLETED` 且 `chunkCount=2`。

- [ ] **步骤 2：编写失败补偿测试**

让 `saveCompleted` 抛异常，断言 `VectorStore.delete()` 收到本批 UUID 字符串、`markFailedInNewTransaction` 收到文档 ID、版本和精简后的失败原因，并且原异常被重新抛出。再为 `DocumentIngestionPersistenceService` 写事务集成测试，断言 `saveCompleted` 中第二个关系写入失败时 chunk 和 `COMPLETED` 状态一起回滚，而 `markFailedInNewTransaction` 使用 `REQUIRES_NEW` 后仍可提交 `FAILED`。

- [ ] **步骤 3：运行并确认生产服务缺失**

运行：`mvn -pl smrag-core -Dtest=DocumentIngestionServiceTest test`

预期：FAIL，`DocumentIngestionService` 不存在。

- [ ] **步骤 4：扩展实体和任务 DTO**

`KnowledgeDocument` 加入 `knowledgeBaseId`、`checksum`、`version`、`failureReason`；`KnowledgeChunk` 加入 `UUID vectorId`、`pageNumber`、`documentVersion`；`DocumentParseTaskDTO` 加入 `knowledgeBaseId`、`version`、`checksum`，并同步全参构造器。

- [ ] **步骤 5：实现 `DocumentIngestionService.process(task)`**

服务按文档 ID 读取并校验版本、校验值和状态；已完成的相同版本直接返回。其余任务依次执行 `PROCESSING`、Reader、Chunker、批量 `VectorStore.add()`，再调用独立 Bean 的 `saveCompleted` 在一个关系事务中写 chunk 和 `COMPLETED`。catch 中把 UUID 转为字符串调用 `VectorStore.delete()`，再调用 `markFailedInNewTransaction` 记录失败状态，然后原样抛出原始入库异常；若向量补偿也失败，将补偿异常作为 suppressed exception 添加，不能覆盖原始异常。

`DocumentIngestionPersistenceService.saveCompleted` 使用 `@Transactional`，先按文档 ID 与版本删除旧 chunk，再批量插入本版本 chunk，并通过带 `WHERE id = ? AND version = ?` 的更新写 `COMPLETED`。`markFailedInNewTransaction` 使用 `@Transactional(propagation = Propagation.REQUIRES_NEW)`，同样按版本条件更新，防止旧 Kafka 任务覆盖新版本状态。事务方法必须放在独立 Bean 中，禁止依赖同类自调用触发 Spring 代理。

- [ ] **步骤 6：让 Consumer 只委托且不吞异常**

```java
@KafkaListener(topics = "document-parse", groupId = "smrag-parse-group")
public void consume(String message) {
    DocumentParseTaskDTO task = JSONUtil.toBean(message, DocumentParseTaskDTO.class);
    ingestionService.process(task);
}
```

- [ ] **步骤 7：配置三次尝试和 DLT**

`DocumentParseRecoverer` 实现 `BiConsumer<ConsumerRecord<?, ?>, Exception>`，解析任务、更新匹配版本为 `FAILED`，并发送到 `document-parse.DLT`。`KafkaRetryConfig` 使用 `ExponentialBackOffWithMaxRetries(2)`，初始间隔 1 秒、倍数 2、最大 10 秒；初始消费加两次重试总计三次尝试。

- [ ] **步骤 8：运行入库、Consumer 和 Recoverer 测试**

运行：

```powershell
mvn -pl smrag-core -Dtest=DocumentIngestionServiceTest,DocumentIngestionPersistenceServiceTest,DocumentParseConsumerTest,DocumentParseRecovererTest test
```

预期：PASS。

- [ ] **步骤 9：提交可靠入库链路**

```powershell
git add -- smrag-core/src/main/java/com/hqc/smrag/entity/KnowledgeDocument.java smrag-core/src/main/java/com/hqc/smrag/entity/KnowledgeChunk.java smrag-core/src/main/java/com/hqc/smrag/dto/DocumentParseTaskDTO.java smrag-core/src/main/java/com/hqc/smrag/service/knowledge/DocumentIngestionService.java smrag-core/src/main/java/com/hqc/smrag/service/knowledge/DocumentIngestionPersistenceService.java smrag-core/src/main/java/com/hqc/smrag/kafka/DocumentParseRecoverer.java smrag-core/src/main/java/com/hqc/smrag/kafka/DocumentParseConsumer.java smrag-core/src/main/java/com/hqc/smrag/config/KafkaRetryConfig.java smrag-core/src/test/java/com/hqc/smrag/service/knowledge/DocumentIngestionServiceTest.java smrag-core/src/test/java/com/hqc/smrag/service/knowledge/DocumentIngestionPersistenceServiceTest.java smrag-core/src/test/java/com/hqc/smrag/kafka/DocumentParseConsumerTest.java smrag-core/src/test/java/com/hqc/smrag/kafka/DocumentParseRecovererTest.java
git commit -m "feat: make document ingestion idempotent"
```

### 任务 6：升级上传、删除与一次性重建向量

**文件：**
- 修改：`smrag-core/src/main/java/com/hqc/smrag/service/knowledge/DocumentService.java`
- 修改：`smrag-core/src/main/java/com/hqc/smrag/controller/KnowledgeController.java`
- 创建：`smrag-core/src/main/java/com/hqc/smrag/service/knowledge/VectorReindexRunner.java`
- 删除：`smrag-core/src/main/java/com/hqc/smrag/utils/ChunkUtils.java`
- 删除：`smrag-core/src/main/java/com/hqc/smrag/utils/TikaUtils.java`
- 修改：`smrag-core/src/test/java/com/hqc/smrag/service/knowledge/DocumentServiceTest.java`
- 创建：`smrag-core/src/test/java/com/hqc/smrag/service/knowledge/VectorReindexRunnerTest.java`
- 删除：`smrag-core/src/test/java/com/hqc/smrag/utils/ChunkUtilsTest.java`

- [ ] **步骤 1：扩展上传测试**

在现有测试中断言上传记录包含 `knowledgeBaseId=default`、SHA-256 `checksum`、`version=1`，Kafka JSON 包含相同字段；再添加超过 `maxUploadSizeBytes` 时拒绝上传的测试。

- [ ] **步骤 2：先写删除顺序测试**

准备文档和两个带 UUID 的 chunk，调用删除后使用 Mockito `InOrder` 断言顺序为 `vectorStore.delete(ids)`、删除 chunk、删除文档。让 vector delete 抛异常，断言关系记录没有删除。

- [ ] **步骤 3：运行并确认新断言失败**

运行：`mvn -pl smrag-core -Dtest=DocumentServiceTest test`

预期：FAIL，现有服务没有 checksum、版本和向量删除。

- [ ] **步骤 4：实现上传与完整删除**

保留 `uploadDocument(MultipartFile)`，内部委托 `uploadDocument(file, "default")`。使用 `MessageDigest.getInstance("SHA-256")` 计算十六进制 checksum；在保存文档前校验配置化大小。删除时先查询 chunk UUID 并调用 `vectorStore.delete(ids)`，成功后删除 chunk、文件和文档。

- [ ] **步骤 5：实现 property-gated reindex runner**

`VectorReindexRunner` 使用 `@ConditionalOnProperty(name = "smrag.ai.rag.reindex-on-startup", havingValue = "true")`。它读取已有 chunk，按文档分组构造 Spring AI `Document`，使用稳定 UUID 补写 `vector_id`，批量写入 `rag_vector_store`，并在日志中输出成功与失败计数。重复运行产生相同 UUID，不新增重复数据。

- [ ] **步骤 6：运行上传、删除和 reindex 测试**

运行：`mvn -pl smrag-core -Dtest=DocumentServiceTest,VectorReindexRunnerTest test`

预期：PASS。

- [ ] **步骤 7：删除已无调用的 `ChunkUtils` 与 `TikaUtils`**

先运行 `rg -n "ChunkUtils|TikaUtils" smrag-core/src/main/java`，确认只剩工具类自身后删除两个工具类及旧 `ChunkUtilsTest`。

- [ ] **步骤 8：提交上传、删除和重建能力**

```powershell
git add -- smrag-core/src/main/java/com/hqc/smrag/controller/KnowledgeController.java smrag-core/src/main/java/com/hqc/smrag/service/knowledge/DocumentService.java smrag-core/src/main/java/com/hqc/smrag/service/knowledge/VectorReindexRunner.java smrag-core/src/main/java/com/hqc/smrag/utils/ChunkUtils.java smrag-core/src/main/java/com/hqc/smrag/utils/TikaUtils.java smrag-core/src/test/java/com/hqc/smrag/service/knowledge/DocumentServiceTest.java smrag-core/src/test/java/com/hqc/smrag/service/knowledge/VectorReindexRunnerTest.java smrag-core/src/test/java/com/hqc/smrag/utils/ChunkUtilsTest.java
git commit -m "feat: complete document vector lifecycle"
```

### 任务 7：阶段验证

**文件：**
- 检查：本计划全部文件

- [ ] **步骤 1：运行全部后端测试**

```powershell
$env:JAVA_HOME='D:\JavaProject\development\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
mvn -pl smrag-core test
```

预期：BUILD SUCCESS，失败数为 0。该命令使用 JDK 21 编译器的 `--release 17`，证明 Java 17 API/字节码约束，但不替代真实 JDK 17 运行验证。

- [ ] **步骤 2：用真实 JDK 17 运行构建**

当前机器没有 JDK 17。获得安装权限后将 JDK 17 安装到 `D:\JavaProject\development\jdk-17`，然后运行：

```powershell
$env:JAVA_HOME='D:\JavaProject\development\jdk-17'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
java -version
mvn -pl smrag-core test
```

预期：`java -version` 显示 17，Maven BUILD SUCCESS。

- [ ] **步骤 3：检查依赖和迁移**

运行：

```powershell
mvn -pl smrag-core dependency:tree "-Dincludes=org.springframework.ai:*,com.alibaba.cloud.ai:*"
```

预期：Spring AI 为 1.1.2，Alibaba 主版本为 1.1.2.0，不含 M2/Milestone 依赖。

- [ ] **步骤 4：检查工作区和阶段提交**

```powershell
git status --short
git log --oneline -7
```

确认本计划产生的文件已提交，原工作区中与本计划无关的修改仍然保留且未被覆盖。
