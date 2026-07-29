# 可选文档切块策略实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 允许用户上传文档时选择默认的 Token 切块或增强的递归字符切块，持久化所选策略，并在文档列表中显示实际策略。

**架构：** 上传接口只接收策略枚举，具体参数继续由 `RagProperties` 统一管理。`DocumentChunker` 负责元数据和稳定 ID，切块算法下沉到 `DocumentSplittingStrategy` 实现；消费端从 `knowledge_document` 读取策略并路由。递归实现逐个源 `Document` 处理，保留分隔符、贪心合并、生成 token 重叠，并用 CL100K_BASE 做最终上限保护。

**技术栈：** Java 21、Spring Boot 3.5、Spring AI 1.1、MyBatis-Plus、Flyway、JTokkit、JUnit 5、Mockito、Vue 3、Pinia、Element Plus、Node Test Runner

---

## 文件结构

### 新建文件

- `smrag-core/src/main/java/com/hqc/smrag/enums/ChunkingStrategy.java`：定义 `TOKEN`、`RECURSIVE`，并集中处理请求值和持久化值解析。
- `smrag-core/src/main/java/com/hqc/smrag/service/knowledge/DocumentSplittingStrategy.java`：切块算法统一接口。
- `smrag-core/src/main/java/com/hqc/smrag/service/knowledge/Cl100kTokenCodec.java`：封装 CL100K_BASE 的计数、按 token 切分和尾部 token 提取。
- `smrag-core/src/main/java/com/hqc/smrag/service/knowledge/TokenDocumentSplittingStrategy.java`：封装当前 `TokenTextSplitter` 行为。
- `smrag-core/src/main/java/com/hqc/smrag/service/knowledge/RecursiveDocumentSplittingStrategy.java`：实现保留分隔符、递归降级、贪心合并和重叠。
- `smrag-core/src/main/resources/db/migration/V12__document_chunking_strategy.sql`：为 `knowledge_document` 增加策略列和约束。
- `smrag-core/src/test/java/com/hqc/smrag/service/knowledge/Cl100kTokenCodecTest.java`：验证 token 编解码边界。
- `smrag-core/src/test/java/com/hqc/smrag/service/knowledge/TokenDocumentSplittingStrategyTest.java`：锁定旧切块行为。
- `smrag-core/src/test/java/com/hqc/smrag/service/knowledge/RecursiveDocumentSplittingStrategyTest.java`：覆盖递归、分隔符、合并、重叠、隔离和限制。
- `smrag-core/src/test/java/com/hqc/smrag/controller/KnowledgeUploadControllerTest.java`：验证上传参数透传和非法参数的 HTTP 400。
- `smrag-web/src/constants/documentChunking.js`：前端策略选项、默认值和显示名称。
- `smrag-web/tests/documentChunking.test.js`：验证策略常量、上传表单字段和页面接线。

### 修改文件

- `pom.xml`、`smrag-core/pom.xml`：将直接使用的 JTokkit 声明为显式依赖。
- `smrag-core/src/main/java/com/hqc/smrag/config/RagProperties.java`、`smrag-core/src/main/resources/application.yml`：增加递归切块参数和交叉校验。
- `smrag-core/src/main/java/com/hqc/smrag/entity/KnowledgeDocument.java`、`smrag-core/src/main/java/com/hqc/smrag/vo/DocumentVO.java`：持久化并返回切块策略。
- `smrag-core/src/main/java/com/hqc/smrag/service/knowledge/DocumentChunker.java`：按枚举路由算法，保留现有元数据与稳定 ID 职责。
- `smrag-core/src/main/java/com/hqc/smrag/service/knowledge/DocumentService.java`：解析上传策略并写入占位记录，同时保留三参数兼容入口。
- `smrag-core/src/main/java/com/hqc/smrag/controller/KnowledgeController.java`：接收可选策略参数并在列表映射策略。
- `smrag-core/src/main/java/com/hqc/smrag/service/knowledge/DocumentIngestionService.java`：从数据库记录读取策略并传给 `DocumentChunker`。
- `smrag-core/src/test/java/com/hqc/smrag/config/RagPropertiesTest.java`：覆盖递归参数约束。
- `smrag-core/src/test/java/com/hqc/smrag/integration/RagSchemaMigrationTest.java`：覆盖列默认值、长度与 CHECK 约束。
- `smrag-core/src/test/java/com/hqc/smrag/service/knowledge/DocumentChunkerTest.java`：覆盖路由、元数据、ID 和源文档边界。
- `smrag-core/src/test/java/com/hqc/smrag/service/knowledge/DocumentMinioUploadTest.java`：覆盖默认和显式策略持久化。
- `smrag-core/src/test/java/com/hqc/smrag/service/knowledge/DocumentIngestionServiceTest.java`：覆盖消费端策略读取与路由。
- `smrag-core/src/test/java/com/hqc/smrag/service/knowledge/DocumentUploadCompletionTest.java`：确认 Kafka 任务 DTO 不重复携带策略。
- `smrag-core/src/test/java/com/hqc/smrag/controller/KnowledgePermissionListTest.java`：覆盖列表返回策略。
- `smrag-web/src/api/knowledge.js`、`smrag-web/src/stores/knowledge.js`：上传调用链增加策略参数。
- `smrag-web/src/views/Knowledge.vue`：增加策略选择器和列表标签。

## 统一接口约定

实现过程中保持下列签名一致：

```java
public enum ChunkingStrategy {
    TOKEN,
    RECURSIVE;

    public static ChunkingStrategy fromRequest(String value);
    public static ChunkingStrategy fromPersisted(String value);
}
```

```java
public interface DocumentSplittingStrategy {
    ChunkingStrategy type();
    List<Document> split(List<Document> documents);
}
```

```java
public List<Document> chunk(
    List<Document> sourceDocuments,
    ChunkingStrategy chunkingStrategy,
    Long knowledgeBaseId,
    String knowledgeBaseCode,
    Long documentId,
    String fileName,
    Integer aclVersion,
    Set<Long> roleIds
)
```

请求参数为空时 `fromRequest` 返回 `TOKEN`；请求参数非法时抛出 `IllegalArgumentException`，由现有全局异常处理映射为 400。`fromPersisted` 对空值和未知值都失败，防止消费端静默采用错误算法。

### 任务 1：持久化切块策略并暴露列表字段

**文件：**
- 创建：`smrag-core/src/main/java/com/hqc/smrag/enums/ChunkingStrategy.java`
- 创建：`smrag-core/src/main/resources/db/migration/V12__document_chunking_strategy.sql`
- 修改：`smrag-core/src/main/java/com/hqc/smrag/entity/KnowledgeDocument.java`
- 修改：`smrag-core/src/main/java/com/hqc/smrag/vo/DocumentVO.java`
- 修改：`smrag-core/src/main/java/com/hqc/smrag/controller/KnowledgeController.java`
- 测试：`smrag-core/src/test/java/com/hqc/smrag/integration/RagSchemaMigrationTest.java`
- 测试：`smrag-core/src/test/java/com/hqc/smrag/controller/KnowledgePermissionListTest.java`

- [ ] **步骤 1：编写迁移和列表字段的失败测试**

在 `RagSchemaMigrationTest` 的最新版本迁移断言中加入：

```java
assertVarcharCapacity(dataSource, "knowledge_document", "chunking_strategy", 16);
assertThat(queryString(dataSource,
    "SELECT chunking_strategy FROM knowledge_document WHERE id = 1"))
    .isEqualTo("TOKEN");

assertThatThrownBy(() -> execute(dataSource,
    "UPDATE knowledge_document SET chunking_strategy = 'UNKNOWN' WHERE id = 1"))
    .isInstanceOf(SQLException.class);
```

在 `KnowledgePermissionListTest` 的文档夹具上设置 `RECURSIVE`，并断言：

```java
assertThat(result.getData().getRecords())
    .extracting(DocumentVO::getChunkingStrategy)
    .containsExactly("RECURSIVE");
```

- [ ] **步骤 2：运行测试并确认因列和 Java 字段不存在而失败**

运行：

```powershell
$env:JAVA_HOME='D:\JavaProject\development\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
mvn -pl smrag-core -Dtest=RagSchemaMigrationTest,KnowledgePermissionListTest test
```

预期：测试编译或迁移断言失败，指出 `chunkingStrategy`/`chunking_strategy` 尚不存在。

- [ ] **步骤 3：实现枚举、迁移、实体字段和列表映射**

`ChunkingStrategy` 使用以下解析规则：

```java
public static ChunkingStrategy fromRequest(String value) {
    if (value == null || value.isBlank()) {
        return TOKEN;
    }
    return parse(value);
}

public static ChunkingStrategy fromPersisted(String value) {
    if (value == null || value.isBlank()) {
        throw new IllegalStateException("文档未记录切块策略");
    }
    try {
        return parse(value);
    }
    catch (IllegalArgumentException ex) {
        throw new IllegalStateException("文档切块策略不受支持: " + value, ex);
    }
}

private static ChunkingStrategy parse(String value) {
    try {
        return valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
    catch (IllegalArgumentException ex) {
        throw new IllegalArgumentException("切块策略不合法，仅支持 TOKEN、RECURSIVE", ex);
    }
}
```

迁移内容：

```sql
ALTER TABLE knowledge_document
    ADD COLUMN chunking_strategy VARCHAR(16) NOT NULL DEFAULT 'TOKEN';

ALTER TABLE knowledge_document
    ADD CONSTRAINT chk_knowledge_document_chunking_strategy
        CHECK (chunking_strategy IN ('TOKEN', 'RECURSIVE'));
```

实体和 VO 均增加 `private String chunkingStrategy;`，列表映射增加：

```java
vo.setChunkingStrategy(document.getChunkingStrategy());
```

- [ ] **步骤 4：运行迁移和列表测试**

运行同步骤 2。预期：两个测试类全部通过。

- [ ] **步骤 5：提交持久化契约**

```powershell
git add smrag-core/src/main/java/com/hqc/smrag/enums/ChunkingStrategy.java smrag-core/src/main/resources/db/migration/V12__document_chunking_strategy.sql smrag-core/src/main/java/com/hqc/smrag/entity/KnowledgeDocument.java smrag-core/src/main/java/com/hqc/smrag/vo/DocumentVO.java smrag-core/src/main/java/com/hqc/smrag/controller/KnowledgeController.java smrag-core/src/test/java/com/hqc/smrag/integration/RagSchemaMigrationTest.java smrag-core/src/test/java/com/hqc/smrag/controller/KnowledgePermissionListTest.java
git commit -m "feat: persist document chunking strategy"
```

### 任务 2：增加递归切块配置和 CL100K_BASE token 工具

**文件：**
- 修改：`pom.xml`
- 修改：`smrag-core/pom.xml`
- 修改：`smrag-core/src/main/java/com/hqc/smrag/config/RagProperties.java`
- 修改：`smrag-core/src/main/resources/application.yml`
- 创建：`smrag-core/src/main/java/com/hqc/smrag/service/knowledge/Cl100kTokenCodec.java`
- 测试：`smrag-core/src/test/java/com/hqc/smrag/config/RagPropertiesTest.java`
- 创建：`smrag-core/src/test/java/com/hqc/smrag/service/knowledge/Cl100kTokenCodecTest.java`

- [ ] **步骤 1：编写配置约束和 token 工具的失败测试**

`RagPropertiesTest` 使用 Jakarta Validator 验证：

```java
@Test
void recursiveDefaultsAreValid() {
    RagProperties properties = new RagProperties();

    assertThat(properties.getRecursiveChunkSize()).isEqualTo(800);
    assertThat(properties.getRecursiveChunkOverlap()).isEqualTo(120);
    assertThat(validator.validate(properties)).isEmpty();
}

@Test
void recursiveOverlapMustBeSmallerThanChunkSize() {
    RagProperties properties = new RagProperties();
    properties.setRecursiveChunkSize(120);
    properties.setRecursiveChunkOverlap(120);

    assertThat(validator.validate(properties)).isNotEmpty();
}

@Test
void recursiveSeparatorsMustBeNonEmptyAndUnique() {
    RagProperties properties = new RagProperties();
    properties.setRecursiveSeparators(List.of("\n", "\n"));

    assertThat(validator.validate(properties)).isNotEmpty();
}
```

`Cl100kTokenCodecTest` 验证中文、英文和边界：

```java
@Test
void splitsAndExtractsWithoutExceedingTokenLimit() {
    String text = "第一段 mixed English content。第二段继续补充信息。";
    List<String> pieces = codec.splitByTokens(text, 5);

    assertThat(pieces).isNotEmpty();
    assertThat(pieces).allMatch(piece -> codec.count(piece) <= 5);
    assertThat(String.join("", pieces)).isEqualTo(text);
    assertThat(codec.count(codec.lastTokens(text, 4))).isLessThanOrEqualTo(4);
}
```

- [ ] **步骤 2：运行测试并确认新配置和工具缺失**

运行：

```powershell
$env:JAVA_HOME='D:\JavaProject\development\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
mvn -pl smrag-core -Dtest=RagPropertiesTest,Cl100kTokenCodecTest test
```

预期：测试编译失败，指出新属性和 `Cl100kTokenCodec` 不存在。

- [ ] **步骤 3：声明依赖并实现配置校验**

根 POM 增加：

```xml
<jtokkit.version>1.1.0</jtokkit.version>
```

核心模块增加：

```xml
<dependency>
    <groupId>com.knuddels</groupId>
    <artifactId>jtokkit</artifactId>
    <version>${jtokkit.version}</version>
</dependency>
```

`RagProperties` 增加默认值：

```java
@Min(1)
private int recursiveChunkSize = 800;

@Min(0)
private int recursiveChunkOverlap = 120;

private List<String> recursiveSeparators =
    List.of("\n\n", "\n", "。", "！", "？", "；", "，", " ");

@AssertTrue(message = "递归切块配置不合法")
public boolean isRecursiveChunkingValid() {
    if (recursiveChunkOverlap >= recursiveChunkSize
        || recursiveSeparators == null
        || recursiveSeparators.isEmpty()) {
        return false;
    }
    Set<String> unique = new HashSet<>();
    return recursiveSeparators.stream()
        .allMatch(separator ->
            separator != null && !separator.isEmpty() && unique.add(separator));
}
```

`application.yml` 在 `smrag.ai.rag` 下写入相同默认值。

- [ ] **步骤 4：实现 token 工具**

核心结构：

```java
@Component
public class Cl100kTokenCodec {
    private final Encoding encoding = Encodings.newLazyEncodingRegistry()
        .getEncoding(EncodingType.CL100K_BASE);

    public int count(String text) {
        return encoding.countTokens(text);
    }

    public List<String> splitByTokens(String text, int maxTokens) {
        IntArrayList tokens = encoding.encode(text);
        List<String> result = new ArrayList<>();
        for (int start = 0; start < tokens.size(); start += maxTokens) {
            IntArrayList slice = new IntArrayList();
            for (int i = start; i < Math.min(start + maxTokens, tokens.size()); i++) {
                slice.add(tokens.get(i));
            }
            result.add(encoding.decode(slice));
        }
        return result;
    }

    public String lastTokens(String text, int maxTokens) {
        if (maxTokens == 0) {
            return "";
        }
        IntArrayList tokens = encoding.encode(text);
        IntArrayList suffix = new IntArrayList();
        for (int i = Math.max(0, tokens.size() - maxTokens); i < tokens.size(); i++) {
            suffix.add(tokens.get(i));
        }
        return encoding.decode(suffix);
    }
}
```

构造参数或方法入口对非正 `maxTokens` 抛出 `IllegalArgumentException`。

- [ ] **步骤 5：运行配置和 token 工具测试**

运行同步骤 2。预期：全部通过。

- [ ] **步骤 6：提交配置和 token 基础设施**

```powershell
git add pom.xml smrag-core/pom.xml smrag-core/src/main/java/com/hqc/smrag/config/RagProperties.java smrag-core/src/main/resources/application.yml smrag-core/src/main/java/com/hqc/smrag/service/knowledge/Cl100kTokenCodec.java smrag-core/src/test/java/com/hqc/smrag/config/RagPropertiesTest.java smrag-core/src/test/java/com/hqc/smrag/service/knowledge/Cl100kTokenCodecTest.java
git commit -m "feat: configure recursive document chunking"
```

### 任务 3：实现增强递归切块策略

**文件：**
- 创建：`smrag-core/src/main/java/com/hqc/smrag/service/knowledge/DocumentSplittingStrategy.java`
- 创建：`smrag-core/src/main/java/com/hqc/smrag/service/knowledge/RecursiveDocumentSplittingStrategy.java`
- 创建：`smrag-core/src/test/java/com/hqc/smrag/service/knowledge/RecursiveDocumentSplittingStrategyTest.java`

- [ ] **步骤 1：编写递归策略的失败测试**

至少覆盖以下独立用例：

```java
@Test
void preservesSeparatorsAndReconstructsTextWithoutOverlap() {
    RagProperties properties = properties(12, 0);
    RecursiveDocumentSplittingStrategy strategy = strategy(properties);
    String text = "第一段。\n\n第二段很长，需要继续拆分！第三段。";

    List<Document> chunks = strategy.split(List.of(document(text, "page", 1)));

    assertThat(chunks).allMatch(chunk -> codec.count(chunk.getText()) <= 12);
    assertThat(chunks).extracting(Document::getText)
        .asString()
        .contains("。", "\n\n", "！");
    assertThat(chunks.stream().map(Document::getText).collect(joining()))
        .isEqualTo(text);
}
```

```java
@Test
void greedilyMergesShortFragmentsAndAddsTokenOverlap() {
    RagProperties properties = properties(10, 2);
    List<Document> chunks = strategy(properties).split(List.of(
        document("甲段。乙段。丙段。丁段。戊段。己段。", "page", 1)));

    assertThat(chunks.size()).isGreaterThan(1);
    assertThat(chunks).allMatch(chunk -> codec.count(chunk.getText()) <= 10);
    assertThat(codec.lastTokens(chunks.get(0).getText(), 2))
        .isEqualTo(chunks.get(1).getText().substring(
            0, codec.lastTokens(chunks.get(0).getText(), 2).length()));
}
```

```java
@Test
void neverMergesDifferentSourceDocuments() {
    Document first = document("第一页内容。", "page", 1);
    Document second = document("第二页内容。", "page", 2);

    List<Document> chunks = strategy(properties(100, 15))
        .split(List.of(first, second));

    assertThat(chunks).hasSize(2);
    assertThat(chunks.get(0).getMetadata()).containsEntry("page", 1);
    assertThat(chunks.get(1).getMetadata()).containsEntry("page", 2);
}
```

再增加：无分隔符文本按 token 硬切、短于 `minChunkLengthToEmbed` 的空白/碎片被过滤、非空输入全部被过滤时失败、超过 `maxNumChunks` 时失败且不截断。

- [ ] **步骤 2：运行测试并确认接口和实现不存在**

运行：

```powershell
$env:JAVA_HOME='D:\JavaProject\development\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
mvn -pl smrag-core -Dtest=RecursiveDocumentSplittingStrategyTest test
```

预期：测试编译失败，指出策略接口或实现不存在。

- [ ] **步骤 3：实现递归拆分和分隔符保留**

递归阶段逐个源文档执行：

```java
private List<String> splitRecursively(String text, int separatorIndex) {
    if (text.isEmpty() || tokenCodec.count(text) <= chunkSize) {
        return text.isEmpty() ? List.of() : List.of(text);
    }
    if (separatorIndex >= separators.size()) {
        return tokenCodec.splitByTokens(text, chunkSize);
    }

    List<String> result = new ArrayList<>();
    for (String piece : splitKeepingSeparator(text, separators.get(separatorIndex))) {
        if (tokenCodec.count(piece) > chunkSize) {
            result.addAll(splitRecursively(piece, separatorIndex + 1));
        }
        else {
            result.add(piece);
        }
    }
    return result;
}
```

分隔符必须按字面量扫描，且附在前一个片段末尾：

```java
private List<String> splitKeepingSeparator(String text, String separator) {
    List<String> pieces = new ArrayList<>();
    int cursor = 0;
    int match;
    while ((match = text.indexOf(separator, cursor)) >= 0) {
        int end = match + separator.length();
        pieces.add(text.substring(cursor, end));
        cursor = end;
    }
    if (cursor < text.length()) {
        pieces.add(text.substring(cursor));
    }
    return pieces.isEmpty() ? List.of(text) : pieces;
}
```

- [ ] **步骤 4：实现贪心合并、重叠和限制**

按原子片段顺序装入当前块，加入下一个片段会超过 `chunkSize` 时输出当前块。下一块优先从已输出块尾部收集完整原子片段，完整片段总 token 数不能超过 `chunkOverlap`；单个尾片段已超过重叠预算时使用 `lastTokens`。若重叠加新片段会超过上限，则缩短或丢弃重叠，绝不截断新片段。

每次输出执行：

```java
private void addChunk(List<Document> output, String text, Document source) {
    if (text.isBlank() || text.length() < minChunkLengthToEmbed) {
        return;
    }
    if (tokenCodec.count(text) > chunkSize) {
        throw new IllegalStateException("递归切块超过 token 上限");
    }
    if (output.size() >= maxNumChunks) {
        throw new IllegalStateException("文档切块数量超过上限: " + maxNumChunks);
    }
    output.add(new Document(text, new HashMap<>(source.getMetadata())));
}
```

每个源 `Document` 使用独立的原子片段、当前块和重叠状态。非空源文档最终没有生成任何有效块时抛出 `IllegalStateException`。

- [ ] **步骤 5：运行递归策略测试**

运行同步骤 2。预期：全部通过。

- [ ] **步骤 6：提交递归策略**

```powershell
git add smrag-core/src/main/java/com/hqc/smrag/service/knowledge/DocumentSplittingStrategy.java smrag-core/src/main/java/com/hqc/smrag/service/knowledge/RecursiveDocumentSplittingStrategy.java smrag-core/src/test/java/com/hqc/smrag/service/knowledge/RecursiveDocumentSplittingStrategyTest.java
git commit -m "feat: add recursive document splitting strategy"
```

### 任务 4：封装 Token 策略并让 DocumentChunker 路由

**文件：**
- 创建：`smrag-core/src/main/java/com/hqc/smrag/service/knowledge/TokenDocumentSplittingStrategy.java`
- 修改：`smrag-core/src/main/java/com/hqc/smrag/service/knowledge/DocumentChunker.java`
- 创建：`smrag-core/src/test/java/com/hqc/smrag/service/knowledge/TokenDocumentSplittingStrategyTest.java`
- 修改：`smrag-core/src/test/java/com/hqc/smrag/service/knowledge/DocumentChunkerTest.java`

- [ ] **步骤 1：锁定 Token 行为并编写路由失败测试**

`TokenDocumentSplittingStrategyTest` 使用当前 `RagProperties` 构造 `TokenTextSplitter`，验证非空、元数据保留和块数上限。

`DocumentChunkerTest` 注入两个可识别的假策略：

```java
DocumentSplittingStrategy recursive = strategy(
    ChunkingStrategy.RECURSIVE, List.of(new Document("recursive")));
DocumentChunker chunker = new DocumentChunker(
    List.of(token, recursive), idGenerator);

List<Document> chunks = chunker.chunk(
    sources, ChunkingStrategy.RECURSIVE,
    1L, "kb", 2L, "file.pdf", 3, Set.of(4L));

assertThat(chunks).extracting(Document::getText).containsExactly("recursive");
verify(recursive).split(sources);
verifyNoInteractions(token);
```

保留原测试对 `knowledgeBaseId`、`knowledgeBaseCode`、`documentId`、`fileName`、`aclVersion`、`roleIds`、`chunkIndex` 和稳定 UUID 的断言。

- [ ] **步骤 2：运行测试并确认构造器和方法签名尚未支持路由**

运行：

```powershell
$env:JAVA_HOME='D:\JavaProject\development\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
mvn -pl smrag-core -Dtest=TokenDocumentSplittingStrategyTest,DocumentChunkerTest test
```

预期：测试编译失败。

- [ ] **步骤 3：封装原 TokenTextSplitter**

```java
@Component
public class TokenDocumentSplittingStrategy implements DocumentSplittingStrategy {
    private final TokenTextSplitter splitter;

    public TokenDocumentSplittingStrategy(RagProperties properties) {
        this.splitter = TokenTextSplitter.builder()
            .withChunkSize(properties.getChunkSize())
            .withMinChunkSizeChars(properties.getMinChunkSizeChars())
            .withMinChunkLengthToEmbed(properties.getMinChunkLengthToEmbed())
            .withMaxNumChunks(properties.getMaxNumChunks())
            .withKeepSeparator(properties.isKeepSeparator())
            .build();
    }

    @Override
    public ChunkingStrategy type() {
        return ChunkingStrategy.TOKEN;
    }

    @Override
    public List<Document> split(List<Document> documents) {
        return splitter.apply(documents);
    }
}
```

- [ ] **步骤 4：重构 DocumentChunker 为策略注册表**

构造时拒绝重复和缺失策略：

```java
public DocumentChunker(
    List<DocumentSplittingStrategy> strategies,
    VectorDocumentIdGenerator idGenerator
) {
    this.strategies = strategies.stream().collect(toUnmodifiableMap(
        DocumentSplittingStrategy::type,
        Function.identity(),
        (left, right) -> {
            throw new IllegalStateException("重复的文档切块策略: " + left.type());
        }));
    for (ChunkingStrategy type : ChunkingStrategy.values()) {
        if (!this.strategies.containsKey(type)) {
            throw new IllegalStateException("缺少文档切块策略: " + type);
        }
    }
    this.idGenerator = idGenerator;
}
```

`chunk` 先调用所选策略，再沿用现有元数据增强、`chunkIndex` 和稳定 ID 逻辑。空策略参数直接失败，不在此层补默认值。

- [ ] **步骤 5：运行 Token 和路由测试**

运行同步骤 2。预期：全部通过。

- [ ] **步骤 6：提交策略路由**

```powershell
git add smrag-core/src/main/java/com/hqc/smrag/service/knowledge/TokenDocumentSplittingStrategy.java smrag-core/src/main/java/com/hqc/smrag/service/knowledge/DocumentChunker.java smrag-core/src/test/java/com/hqc/smrag/service/knowledge/TokenDocumentSplittingStrategyTest.java smrag-core/src/test/java/com/hqc/smrag/service/knowledge/DocumentChunkerTest.java
git commit -m "refactor: route document chunking strategies"
```

### 任务 5：接通上传参数、持久化和异步消费

**文件：**
- 修改：`smrag-core/src/main/java/com/hqc/smrag/controller/KnowledgeController.java`
- 修改：`smrag-core/src/main/java/com/hqc/smrag/service/knowledge/DocumentService.java`
- 修改：`smrag-core/src/main/java/com/hqc/smrag/service/knowledge/DocumentIngestionService.java`
- 创建：`smrag-core/src/test/java/com/hqc/smrag/controller/KnowledgeUploadControllerTest.java`
- 修改：`smrag-core/src/test/java/com/hqc/smrag/service/knowledge/DocumentMinioUploadTest.java`
- 修改：`smrag-core/src/test/java/com/hqc/smrag/service/knowledge/DocumentIngestionServiceTest.java`
- 修改：`smrag-core/src/test/java/com/hqc/smrag/service/knowledge/DocumentUploadCompletionTest.java`

- [ ] **步骤 1：编写上传和消费路由的失败测试**

控制器测试验证省略参数时仍可上传，以及显式值原样传到服务：

```java
mockMvc.perform(multipart("/api/knowledge/documents")
        .file(file)
        .param("knowledgeBaseCode", "kb")
        .param("roles", "admin")
        .param("chunkingStrategy", "RECURSIVE"))
    .andExpect(status().isOk());

verify(documentService).uploadDocument(
    any(MultipartFile.class), eq("kb"), eq(List.of("admin")), eq("RECURSIVE"));
```

`DocumentMinioUploadTest` 捕获占位实体：

```java
assertThat(documentCaptor.getValue().getChunkingStrategy()).isEqualTo("TOKEN");
```

另一个用例显式传入 `"recursive"` 并断言持久化为 `"RECURSIVE"`；非法值断言抛出 `IllegalArgumentException` 且没有写 MinIO/数据库。

`DocumentIngestionServiceTest` 将数据库实体设置为 `RECURSIVE` 并验证：

```java
verify(documentChunker).chunk(
    eq(parsedDocuments),
    eq(ChunkingStrategy.RECURSIVE),
    eq(knowledgeBaseId),
    eq(knowledgeBaseCode),
    eq(documentId),
    eq(fileName),
    eq(aclVersion),
    eq(roleIds));
```

`DocumentUploadCompletionTest` 对 outbox JSON 增加：

```java
assertThat(outboxEvent.getPayload()).doesNotContain("chunkingStrategy");
```

- [ ] **步骤 2：运行上传和消费测试并确认失败**

运行：

```powershell
$env:JAVA_HOME='D:\JavaProject\development\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
mvn -pl smrag-core -Dtest=KnowledgeUploadControllerTest,DocumentMinioUploadTest,DocumentIngestionServiceTest,DocumentUploadCompletionTest test
```

预期：测试因新参数、字段持久化或 `chunk` 签名不匹配而失败。

- [ ] **步骤 3：实现上传接口和服务默认值**

控制器参数：

```java
@RequestParam(value = "chunkingStrategy", required = false)
String chunkingStrategy
```

调用四参数服务方法。`DocumentService` 保留现有三参数入口：

```java
public Long uploadDocument(
    MultipartFile file,
    String knowledgeBaseCode,
    List<String> roleCodes
) {
    return uploadDocument(file, knowledgeBaseCode, roleCodes, null);
}

public Long uploadDocument(
    MultipartFile file,
    String knowledgeBaseCode,
    List<String> roleCodes,
    String chunkingStrategyValue
) {
    ChunkingStrategy strategy =
        ChunkingStrategy.fromRequest(chunkingStrategyValue);
    // 继续现有上传流程
    document.setChunkingStrategy(strategy.name());
}
```

策略解析在文件上传和数据库写入之前执行，确保非法值无副作用。

- [ ] **步骤 4：让消费端读取数据库策略**

```java
ChunkingStrategy strategy =
    ChunkingStrategy.fromPersisted(document.getChunkingStrategy());

List<Document> chunks = documentChunker.chunk(
    parsedDocuments,
    strategy,
    knowledgeBase.getId(),
    knowledgeBase.getCode(),
    document.getId(),
    document.getFileName(),
    document.getAclVersion(),
    roleIds);
```

`DocumentParseTaskDTO` 和 outbox payload 不增加策略字段。测试夹具中所有进入消费流程的 `KnowledgeDocument` 均显式设置 `TOKEN` 或 `RECURSIVE`。

- [ ] **步骤 5：运行上传和消费测试**

运行同步骤 2。预期：全部通过。

- [ ] **步骤 6：运行所有知识库相关回归测试**

运行：

```powershell
$env:JAVA_HOME='D:\JavaProject\development\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
mvn -pl smrag-core -Dtest=DocumentServiceTest,DocumentMinioUploadTest,DocumentUploadPersistenceServiceTest,DocumentUploadCompletionTest,DocumentIngestionServiceTest,DocumentChunkerTest,KnowledgePermissionListTest test
```

预期：全部通过。

- [ ] **步骤 7：提交端到端后端接线**

```powershell
git add smrag-core/src/main/java/com/hqc/smrag/controller/KnowledgeController.java smrag-core/src/main/java/com/hqc/smrag/service/knowledge/DocumentService.java smrag-core/src/main/java/com/hqc/smrag/service/knowledge/DocumentIngestionService.java smrag-core/src/test/java/com/hqc/smrag/controller/KnowledgeUploadControllerTest.java smrag-core/src/test/java/com/hqc/smrag/service/knowledge/DocumentMinioUploadTest.java smrag-core/src/test/java/com/hqc/smrag/service/knowledge/DocumentIngestionServiceTest.java smrag-core/src/test/java/com/hqc/smrag/service/knowledge/DocumentUploadCompletionTest.java
git commit -m "feat: select chunking strategy on upload"
```

### 任务 6：增加前端策略选择器和文档标签

**文件：**
- 创建：`smrag-web/src/constants/documentChunking.js`
- 修改：`smrag-web/src/api/knowledge.js`
- 修改：`smrag-web/src/stores/knowledge.js`
- 修改：`smrag-web/src/views/Knowledge.vue`
- 创建：`smrag-web/tests/documentChunking.test.js`

- [ ] **步骤 1：编写前端失败测试**

策略常量测试：

```javascript
import {
  DEFAULT_CHUNKING_STRATEGY,
  DOCUMENT_CHUNKING_STRATEGIES,
  chunkingStrategyLabel
} from '../src/constants/documentChunking.js'

test('token is the default and both strategies have labels', () => {
  assert.equal(DEFAULT_CHUNKING_STRATEGY, 'TOKEN')
  assert.deepEqual(
    DOCUMENT_CHUNKING_STRATEGIES.map(item => item.value),
    ['TOKEN', 'RECURSIVE']
  )
  assert.equal(chunkingStrategyLabel('RECURSIVE'), '递归切块')
  assert.equal(chunkingStrategyLabel('UNKNOWN'), '未知策略')
})
```

读取 API、store 和 `Knowledge.vue` 源码，断言存在：

```javascript
assert.match(apiSource, /formData\.append\('chunkingStrategy', chunkingStrategy\)/)
assert.match(storeSource, /uploadDocument\(file,\s*knowledgeBaseCode,\s*roles,\s*chunkingStrategy\)/)
assert.match(viewSource, /uploadChunkingStrategy/)
assert.match(viewSource, /chunkingStrategyLabel\(row\.chunkingStrategy\)/)
```

- [ ] **步骤 2：运行前端测试并确认失败**

运行：

```powershell
Set-Location smrag-web
npm.cmd test
```

预期：新测试因常量模块和页面接线不存在而失败。

- [ ] **步骤 3：实现策略常量、API 和 store 参数**

```javascript
export const DEFAULT_CHUNKING_STRATEGY = 'TOKEN'

export const DOCUMENT_CHUNKING_STRATEGIES = Object.freeze([
  { value: 'TOKEN', label: 'Token 切块' },
  { value: 'RECURSIVE', label: '递归切块' }
])

const labels = Object.fromEntries(
  DOCUMENT_CHUNKING_STRATEGIES.map(item => [item.value, item.label])
)

export const chunkingStrategyLabel = value => labels[value] ?? '未知策略'
```

API 的 `FormData` 增加：

```javascript
formData.append('chunkingStrategy', chunkingStrategy)
```

store 的 `uploadDocument` 同步增加第四个参数并传给 API。

- [ ] **步骤 4：实现页面选择器和列表标签**

脚本状态：

```javascript
const uploadChunkingStrategy = ref(DEFAULT_CHUNKING_STRATEGY)
```

上传区域增加单选下拉：

```vue
<el-select
  v-model="uploadChunkingStrategy"
  class="chunking-strategy-select"
  :placeholder="copy.chunkingStrategyPlaceholder"
>
  <el-option
    v-for="item in DOCUMENT_CHUNKING_STRATEGIES"
    :key="item.value"
    :label="item.label"
    :value="item.value"
  />
</el-select>
```

上传调用传入 `uploadChunkingStrategy.value`。成功后只清空文件，不重置策略；页面刷新由 `ref` 初始值恢复 `TOKEN`。

文档表格增加“切块方式”列：

```vue
<el-table-column :label="copy.chunkingStrategy" width="130">
  <template #default="{ row }">
    <el-tag size="small" effect="plain">
      {{ chunkingStrategyLabel(row.chunkingStrategy) }}
    </el-tag>
  </template>
</el-table-column>
```

- [ ] **步骤 5：运行前端测试和生产构建**

运行：

```powershell
Set-Location smrag-web
npm.cmd test
npm.cmd run build
```

预期：测试全部通过，构建成功；允许保留现有 `@vueuse/core` 的第三方 pure annotation 警告。

- [ ] **步骤 6：提交前端功能**

```powershell
git add smrag-web/src/constants/documentChunking.js smrag-web/src/api/knowledge.js smrag-web/src/stores/knowledge.js smrag-web/src/views/Knowledge.vue smrag-web/tests/documentChunking.test.js
git commit -m "feat: choose document chunking strategy in web"
```

### 任务 7：完整验证和变更审计

**文件：**
- 检查：上述全部生产代码、测试、迁移和配置文件

- [ ] **步骤 1：运行后端定向测试**

```powershell
$env:JAVA_HOME='D:\JavaProject\development\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
mvn -pl smrag-core -Dtest=RagPropertiesTest,Cl100kTokenCodecTest,TokenDocumentSplittingStrategyTest,RecursiveDocumentSplittingStrategyTest,DocumentChunkerTest,KnowledgeUploadControllerTest,DocumentMinioUploadTest,DocumentIngestionServiceTest,DocumentUploadCompletionTest,KnowledgePermissionListTest,RagSchemaMigrationTest test
```

预期：全部通过。

- [ ] **步骤 2：运行后端完整测试**

```powershell
$env:JAVA_HOME='D:\JavaProject\development\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
mvn -pl smrag-core test
```

预期：本功能涉及的测试通过。基线中已有且用户明确要求忽略的 `RoutePromptContractTest.separatesPrivateKnowledgeFromGeneralQuestions` 可能仍以旧提示词精确匹配失败；确认没有新增失败，不修改该无关测试。

- [ ] **步骤 3：运行前端完整测试和构建**

```powershell
Set-Location smrag-web
npm.cmd test
npm.cmd run build
Set-Location ..
```

预期：测试全部通过且生产构建成功。

- [ ] **步骤 4：检查数据库、消息和边界不变量**

逐项确认：

- `knowledge_document.chunking_strategy` 对旧记录默认 `TOKEN`，只接受两个枚举值。
- 上传请求为空策略走 `TOKEN`，非法策略返回 400 且不产生上传副作用。
- `DocumentParseTaskDTO` 和 outbox JSON 不含策略，消费端从数据库读取。
- 递归策略不跨源 `Document` 合并或重叠，PDF 页元数据保持隔离。
- 每个递归块不超过 token 上限，超出最大块数时失败而非截断。
- 原 Token 策略仍使用原有五个配置项。
- 页面成功上传后保留当前策略，刷新后默认 `TOKEN`。

- [ ] **步骤 5：检查工作树和差异**

```powershell
git status --short
git diff --check
git log --oneline -8
```

预期：`git diff --check` 无输出；仅保留用户原有的 `.tmp/` 和无法归因于本功能的既有工作树状态，不将其加入提交。
