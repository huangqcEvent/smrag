# DashScope Embedding 批量上限修复实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 保证所有通过 PGVector 发往 DashScope 的 embedding 请求最多包含 25 条文本，同时保留 Spring AI 原有的 token 上限分批。

**架构：** 新增一个组合式 `BatchingStrategy`，先调用 `TokenCountBatchingStrategy`，再把每个结果批次按 25 条切分。通过应用级 `@Bean` 让 PGVector 自动配置使用该策略，上传、重建索引和其他 `VectorStore.add` 入口无需分别修改。

**技术栈：** Java 21、Spring Boot 3.5、Spring AI 1.1.2、JUnit 5、AssertJ

---

## 文件结构

- 创建：`smrag-core/src/main/java/com/hqc/smrag/config/DashScopeEmbeddingBatchingStrategy.java`
  - 组合 token 分批和 DashScope 25 条限制，不修改文档内容与 metadata。
- 创建：`smrag-core/src/main/java/com/hqc/smrag/config/EmbeddingBatchingConfiguration.java`
  - 向 Spring 容器提供应用级 `BatchingStrategy` Bean。
- 创建：`smrag-core/src/test/java/com/hqc/smrag/config/DashScopeEmbeddingBatchingStrategyTest.java`
  - 验证条数上限、委托批次边界、顺序和 Bean 注册。

### 任务 1：实现 DashScope 组合分批策略

**文件：**
- 创建：`smrag-core/src/test/java/com/hqc/smrag/config/DashScopeEmbeddingBatchingStrategyTest.java`
- 创建：`smrag-core/src/main/java/com/hqc/smrag/config/DashScopeEmbeddingBatchingStrategy.java`
- 创建：`smrag-core/src/main/java/com/hqc/smrag/config/EmbeddingBatchingConfiguration.java`

- [ ] **步骤 1：编写失败的策略测试**

```java
class DashScopeEmbeddingBatchingStrategyTest {

    @Test
    void limitsEveryEmbeddingBatchToTwentyFiveDocuments() {
        List<Document> documents = documents(26);
        BatchingStrategy delegate = input -> List.of(input);

        List<List<Document>> batches =
            new DashScopeEmbeddingBatchingStrategy(delegate).batch(documents);

        assertThat(batches).extracting(List::size).containsExactly(25, 1);
        assertThat(batches.stream().flatMap(List::stream).toList())
            .containsExactlyElementsOf(documents);
    }

    @Test
    void preservesDelegateBatchBoundariesWhileApplyingDocumentLimit() {
        List<Document> documents = documents(60);
        BatchingStrategy delegate = input ->
            List.of(input.subList(0, 40), input.subList(40, 60));

        List<List<Document>> batches =
            new DashScopeEmbeddingBatchingStrategy(delegate).batch(documents);

        assertThat(batches).extracting(List::size).containsExactly(25, 15, 20);
        assertThat(batches.stream().flatMap(List::stream).toList())
            .containsExactlyElementsOf(documents);
    }

    @Test
    void configurationRegistersApplicationBatchingStrategy() {
        try (AnnotationConfigApplicationContext context =
                 new AnnotationConfigApplicationContext(EmbeddingBatchingConfiguration.class)) {
            assertThat(context.getBean(BatchingStrategy.class))
                .isInstanceOf(DashScopeEmbeddingBatchingStrategy.class);
        }
    }

    private List<Document> documents(int count) {
        return IntStream.range(0, count)
            .mapToObj(index -> new Document("chunk-" + index))
            .toList();
    }
}
```

- [ ] **步骤 2：运行测试并验证红灯**

运行：

```powershell
$env:JAVA_HOME='D:\JavaProject\development\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
mvn -pl smrag-core -Dtest=DashScopeEmbeddingBatchingStrategyTest test
```

预期：测试编译失败，提示找不到 `DashScopeEmbeddingBatchingStrategy` 和
`EmbeddingBatchingConfiguration`。

- [ ] **步骤 3：编写最少实现**

```java
public final class DashScopeEmbeddingBatchingStrategy implements BatchingStrategy {

    static final int MAX_TEXTS_PER_REQUEST = 25;

    private final BatchingStrategy tokenBatchingStrategy;

    public DashScopeEmbeddingBatchingStrategy() {
        this(new TokenCountBatchingStrategy());
    }

    DashScopeEmbeddingBatchingStrategy(BatchingStrategy tokenBatchingStrategy) {
        this.tokenBatchingStrategy =
            Objects.requireNonNull(tokenBatchingStrategy, "tokenBatchingStrategy");
    }

    @Override
    public List<List<Document>> batch(List<Document> documents) {
        List<List<Document>> batches = new ArrayList<>();
        for (List<Document> tokenBatch : tokenBatchingStrategy.batch(documents)) {
            for (int start = 0; start < tokenBatch.size();
                 start += MAX_TEXTS_PER_REQUEST) {
                int end = Math.min(start + MAX_TEXTS_PER_REQUEST, tokenBatch.size());
                batches.add(List.copyOf(tokenBatch.subList(start, end)));
            }
        }
        return batches;
    }
}
```

```java
@Configuration(proxyBeanMethods = false)
public class EmbeddingBatchingConfiguration {

    @Bean
    BatchingStrategy pgVectorEmbeddingBatchingStrategy() {
        return new DashScopeEmbeddingBatchingStrategy();
    }
}
```

- [ ] **步骤 4：运行目标测试并验证绿灯**

运行：

```powershell
mvn -pl smrag-core -Dtest=DashScopeEmbeddingBatchingStrategyTest test
```

预期：3 个测试全部通过。

- [ ] **步骤 5：运行相关摄取和重建测试**

运行：

```powershell
mvn -pl smrag-core "-Dtest=DocumentIngestionServiceTest,VectorReindexRunnerTest,DashScopeEmbeddingBatchingStrategyTest" test
```

预期：所有相关测试通过，现有补偿删除和权限同步行为不变。

- [ ] **步骤 6：提交策略实现**

```powershell
git add -- `
  smrag-core/src/main/java/com/hqc/smrag/config/DashScopeEmbeddingBatchingStrategy.java `
  smrag-core/src/main/java/com/hqc/smrag/config/EmbeddingBatchingConfiguration.java `
  smrag-core/src/test/java/com/hqc/smrag/config/DashScopeEmbeddingBatchingStrategyTest.java
git commit -m "fix: limit DashScope embedding batch size"
```

### 任务 2：全量回归验证

**文件：**
- 验证：`smrag-core/src/test/java`

- [ ] **步骤 1：运行 `smrag-core` 全量测试**

运行：

```powershell
mvn -pl smrag-core test
```

预期：全部测试通过，无失败和错误。

- [ ] **步骤 2：检查最终变更**

运行：

```powershell
git diff --check
git status --short
git log -3 --oneline
```

预期：`git diff --check` 无输出；工作区不存在未提交的本次实现文件；提交历史包含设计、计划和修复提交。
