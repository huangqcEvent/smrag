# Spring AI 流式体验、可观测性与评测实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。所有前端设计与实现任务必须先加载用户级 `apple-design` skill，并结合本项目企业工作台的产品约束执行。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 在同步聊天稳定后增加可取消的 SSE 流式回答，重排 Vue 聊天工作台并展示结构化引用，同时为 Spring AI 模型、检索、工具和入库流程补齐指标、健康检查与离线相关性评测。

**架构：** 后端路由先于流式订阅完成，`AiChatStreamingService` 将 Spring AI `Flux<ChatClientResponse>` 转为 `meta/delta/citations/done/error` 事件。前端用原生 `fetch` 读取 POST SSE，Pinia 只维护一个增量消息对象。Actuator/Micrometer 采集聚合指标但不记录 prompt；评测放在显式 profile 下，普通构建不调用真实模型。

**界面基调：** 使用者是白天在办公室持续核查基金资料来源的投研、运营和客服人员，界面采用明亮、低眩光、信息密度适中的工作台，而不是营销式深色渐变页。按 `apple-design` 的 clarity、response、spatial consistency 和 restraint 原则处理反馈与层级；颜色策略为 restrained：冷中性色为主、绿色只用于主动作与当前状态、琥珀和红色仅表达风险与错误。

**技术栈：** Spring WebFlux/Reactor、Spring AI ChatClient、SSE、Actuator、Micrometer、Vue 3、Pinia、Element Plus Icons、Vitest、Vue Test Utils。

**前置条件：** 先完成 `2026-07-16-spring-ai-chat-rag.md`，复用其 AiChatService、知识 Advisor、FundTools、引用模型和异常体系。

---

## 文件结构

- 修改：`smrag-core/pom.xml`
- 修改：`smrag-core/src/main/java/com/hqc/smrag/controller/ChatController.java`
- 创建：`smrag-core/src/main/java/com/hqc/smrag/dto/ChatStreamEvent.java`
- 创建：`smrag-core/src/main/java/com/hqc/smrag/service/chat/AiChatStreamingService.java`
- 创建：`smrag-core/src/main/java/com/hqc/smrag/service/chat/ChatCompletionPersister.java`
- 创建：`smrag-core/src/main/java/com/hqc/smrag/service/chat/StreamingBranchClient.java`
- 创建：`smrag-core/src/main/java/com/hqc/smrag/service/chat/SpringAiStreamingBranchClient.java`
- 创建：`smrag-core/src/main/java/com/hqc/smrag/observation/RagMetrics.java`
- 修改：`smrag-core/src/main/resources/application.yml`
- 创建：`smrag-core/src/test/resources/application-evaluation.yml`
- 创建：`smrag-core/src/test/resources/evaluation/rag-cases.json`
- 创建：`smrag-core/src/test/java/com/hqc/smrag/service/chat/AiChatStreamingServiceTest.java`
- 创建：`smrag-core/src/test/java/com/hqc/smrag/controller/ChatStreamingControllerTest.java`
- 创建：`smrag-core/src/test/java/com/hqc/smrag/dto/ChatStreamEventTest.java`
- 创建：`smrag-core/src/test/java/com/hqc/smrag/observation/RagMetricsTest.java`
- 创建：`smrag-core/src/test/java/com/hqc/smrag/evaluation/RagRelevancyEvaluationTest.java`
- 修改：`smrag-web/package.json`
- 修改：`smrag-web/package-lock.json`
- 修改：`smrag-web/vite.config.js`
- 修改：`smrag-web/src/api/chat.js`
- 创建：`smrag-web/src/api/sse.js`
- 修改：`smrag-web/src/stores/chat.js`
- 修改：`smrag-web/src/views/Chat.vue`
- 创建：`smrag-web/src/api/sse.spec.js`
- 创建：`smrag-web/src/stores/chat.spec.js`
- 创建：`smrag-web/src/views/Chat.spec.js`

工作区已有未提交改动。开始每个任务前先保存 `git status --short` 和相关文件的 `git diff`；只暂存本任务新增的 hunks，不使用 `git add .`。若列出的文件在任务前已有无关修改，使用 IDE selective staging 分离 hunks；无法可靠分离时跳过该任务提交并在验收报告中列出，不能把既有修改一起提交。

### 任务 1：定义稳定的 SSE 事件协议

**文件：**

- 创建：`smrag-core/src/main/java/com/hqc/smrag/dto/ChatStreamEvent.java`
- 创建：`smrag-core/src/test/java/com/hqc/smrag/dto/ChatStreamEventTest.java`

- [ ] **步骤 1：先写序列化测试**

使用 `ObjectMapper` 分别序列化五类事件，断言字段稳定且 null 字段不输出。测试数据如下：

```java
ChatStreamEvent.meta("s-1", "KNOWLEDGE_QA");
ChatStreamEvent.delta("回答片段");
ChatStreamEvent.citations(List.of(citation));
ChatStreamEvent.done();
ChatStreamEvent.error("AI_PROVIDER_UNAVAILABLE", "智能服务暂时不可用，请稍后重试。");
```

- [ ] **步骤 2：运行测试并确认失败**

```powershell
$env:JAVA_HOME='D:\JavaProject\development\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
mvn -pl smrag-core -Dtest=ChatStreamEventTest test
```

- [ ] **步骤 3：实现不可变事件对象**

```java
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatStreamEvent(
    String type,
    String sessionId,
    String routeType,
    String delta,
    List<CitationDTO> citations,
    String errorCode,
    String message
) {
    public static ChatStreamEvent meta(String sessionId, String routeType) {
        return new ChatStreamEvent("meta", sessionId, routeType, null, null, null, null);
    }

    public static ChatStreamEvent delta(String delta) {
        return new ChatStreamEvent("delta", null, null, delta, null, null, null);
    }

    public static ChatStreamEvent citations(List<CitationDTO> citations) {
        return new ChatStreamEvent("citations", null, null, null, List.copyOf(citations), null, null);
    }

    public static ChatStreamEvent done() {
        return new ChatStreamEvent("done", null, null, null, null, null, null);
    }

    public static ChatStreamEvent error(String code, String message) {
        return new ChatStreamEvent("error", null, null, null, null, code, message);
    }
}
```

HTTP SSE 的 `event:` 名称与 payload 的 `type` 保持一致，便于浏览器调试、代理日志和非浏览器客户端消费。

- [ ] **步骤 4：验证并提交**

```powershell
mvn -pl smrag-core -Dtest=ChatStreamEventTest test
git add smrag-core/src/main/java/com/hqc/smrag/dto/ChatStreamEvent.java smrag-core/src/test/java/com/hqc/smrag/dto/ChatStreamEventTest.java
git commit -m "feat: define chat stream events"
```

### 任务 2：实现可取消且只持久化完整回答的 Spring AI 流

**文件：**

- 修改：`smrag-core/pom.xml`
- 创建：`smrag-core/src/main/java/com/hqc/smrag/service/chat/ChatCompletionPersister.java`
- 创建：`smrag-core/src/main/java/com/hqc/smrag/service/chat/AiChatStreamingService.java`
- 创建：`smrag-core/src/main/java/com/hqc/smrag/service/chat/StreamingBranchClient.java`
- 创建：`smrag-core/src/main/java/com/hqc/smrag/service/chat/SpringAiStreamingBranchClient.java`
- 修改：`smrag-core/src/main/java/com/hqc/smrag/controller/ChatController.java`
- 创建：`smrag-core/src/test/java/com/hqc/smrag/service/chat/AiChatStreamingServiceTest.java`
- 创建：`smrag-core/src/test/java/com/hqc/smrag/controller/ChatStreamingControllerTest.java`

- [ ] **步骤 1：补充 WebFlux 与 Reactor 测试依赖并写流顺序测试**

POM 增加 `spring-boot-starter-webflux`，并显式以 test scope 增加 `reactor-test`。项目同时保留 `spring-boot-starter-web`，Spring Boot 继续使用 SERVLET 应用类型，而 ChatController 可以返回 Reactor Publisher。用 `StepVerifier` 断言成功流严格为：一个 `meta`、一个或多个 `delta`、一个 `citations`、一个 `done`。把模型响应拆成“答”“案”两个片段，断言持久化器只调用一次且收到“答案”。

- [ ] **步骤 2：写取消和异常测试**

- 订阅两个 delta 后调用 `thenCancel()`，断言上游收到 cancel，持久化器未调用。
- 上游模型在首个 delta 后报错，断言最后收到一个 `error` 事件且不持久化。
- citations 只从流式 `ChatClientResponse.context()` 中的 `RetrievalAugmentationAdvisor.DOCUMENT_CONTEXT` 组装，不允许额外调用 VectorStore。

- [ ] **步骤 3：运行测试并确认失败**

```powershell
mvn -pl smrag-core -Dtest=AiChatStreamingServiceTest,ChatStreamingControllerTest test
```

- [ ] **步骤 4：实现流式编排**

`AiChatStreamingService.stream(request)` 使用 `Flux.defer`，每个订阅单独持有 `StringBuilder answer` 和 `AtomicReference<List<Document>> retrievedDocuments`。改写和路由完成后：

```java
Flux<ChatStreamEvent> deltas = branchClient.stream(context)
    .handle((response, sink) -> {
        captureDocuments(response, retrievedDocuments);
        String text = response.chatResponse().getResult().getOutput().getText();
        if (StringUtils.hasText(text)) {
            answer.append(text);
            sink.next(ChatStreamEvent.delta(text));
        }
    });

Mono<ChatStreamEvent> citations = Mono.fromSupplier(() ->
    ChatStreamEvent.citations(citationAssembler.from(retrievedDocuments.get()))
);

Mono<ChatStreamEvent> persistAndDone = Mono.fromCallable(() -> {
        completionPersister.persist(context, answer.toString(), retrievedDocuments.get());
        return ChatStreamEvent.done();
    })
    .subscribeOn(Schedulers.boundedElastic());

return Flux.concat(
    Mono.just(ChatStreamEvent.meta(context.sessionId(), context.routeType().name())),
    deltas,
    citations,
    persistAndDone
).onErrorResume(errorMapper::toStreamEvent);
```

流式分支从 `ChatMemoryService.history(sessionId)` 读取历史并显式加入 Prompt，但不注册 `MessageChatMemoryAdvisor`，避免 Advisor 在客户端取消时提前写入用户消息。`completionPersister.persist` 是独立 `@Transactional` Bean，只有完整回答结束后才同时追加 user/assistant 两条 ChatMemory 消息并写 `chat_session`。取消订阅会中止 concat，因而不会执行尾部 persistence Mono。空答案视为失败，不写记录。不要在 Reactor 事件循环执行 JDBC。

`StreamingBranchClient` 定义 `Flux<ChatClientResponse> stream(StreamingChatContext context)`，其中 `StreamingChatContext` 作为该接口的嵌套 record 保存原问题、改写问题、会话、路由、字符串 knowledgeBaseId、可选 documentId 和历史。`SpringAiStreamingBranchClient` 按 route 复用知识 Advisor、业务 `ToolCallback` 选择和销售 prompt；它把 history 作为 messages 显式加入请求，并且不注册 Memory Advisor。知识分支仍从同一批 `ChatClientResponse.context()` 读取检索文档。

- [ ] **步骤 5：暴露 POST SSE 接口**

```java
@PostMapping(value = "/ask/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<ServerSentEvent<ChatStreamEvent>> stream(@Valid @RequestBody ChatRequestDTO request) {
    return streamingService.stream(request)
        .map(event -> ServerSentEvent.<ChatStreamEvent>builder(event)
            .event(event.type())
            .build());
}
```

不使用 `SseEmitter`，避免另建线程和手工生命周期管理。同步 `/ask` 保留不变。

- [ ] **步骤 6：运行测试并提交**

```powershell
mvn -pl smrag-core -Dtest=AiChatStreamingServiceTest,ChatStreamingControllerTest test
git add smrag-core/pom.xml smrag-core/src/main/java/com/hqc/smrag/service/chat/ChatCompletionPersister.java smrag-core/src/main/java/com/hqc/smrag/service/chat/AiChatStreamingService.java smrag-core/src/main/java/com/hqc/smrag/service/chat/StreamingBranchClient.java smrag-core/src/main/java/com/hqc/smrag/service/chat/SpringAiStreamingBranchClient.java smrag-core/src/main/java/com/hqc/smrag/controller/ChatController.java smrag-core/src/test/java/com/hqc/smrag/service/chat/AiChatStreamingServiceTest.java smrag-core/src/test/java/com/hqc/smrag/controller/ChatStreamingControllerTest.java
git commit -m "feat: stream spring ai chat responses"
```

### 任务 3：建立可测试的 POST SSE 客户端

**文件：**

- 修改：`smrag-web/package.json`
- 修改：`smrag-web/package-lock.json`
- 修改：`smrag-web/vite.config.js`
- 创建：`smrag-web/src/api/sse.js`
- 修改：`smrag-web/src/api/chat.js`
- 创建：`smrag-web/src/api/sse.spec.js`

- [ ] **步骤 1：安装前端测试依赖**

```powershell
Set-Location smrag-web
npm install --save-dev vitest @vue/test-utils jsdom
```

在 `package.json` 添加 `"test": "vitest run"`，在 Vite 配置添加 `test: { environment: 'jsdom', globals: true }`。

- [ ] **步骤 2：先写 SSE 分块解析测试**

测试覆盖：一个网络 chunk 含多个事件、一个事件跨两个 chunk、CRLF、多行 `data:`、非 2xx 响应、服务端 `error` 事件和 AbortSignal。Mock `fetch` 返回由 `ReadableStream` 构造的 response。

```javascript
it('parses an event split across network chunks', async () => {
  const chunks = ['event: del', 'ta\ndata: {"type":"delta","delta":"答"}\n\n']
  mockFetchStream(chunks)
  const events = []

  await consumeSse('/api/chat/ask/stream', {}, event => events.push(event))

  expect(events).toEqual([{ type: 'delta', delta: '答' }])
})
```

- [ ] **步骤 3：实现通用解析器和聊天 API**

`consumeSse(url, body, onEvent, { signal })` 使用 `fetch` POST JSON，循环 `response.body.getReader().read()`，通过 `TextDecoder.decode(value, { stream: true })` 累积文本。以空行切事件块，连接同一块内所有 `data:` 行后执行 `JSON.parse`。收到无法解析的 JSON 时抛出带安全消息的 `StreamProtocolError`。

`streamChat(data, onEvent, signal)` 只包装固定 URL，不把 token 或 provider 信息放在 query string。

- [ ] **步骤 4：运行测试并提交**

```powershell
npm test -- --run src/api/sse.spec.js
git add package.json package-lock.json vite.config.js src/api/sse.js src/api/chat.js src/api/sse.spec.js
git commit -m "feat: add tested post sse client"
```

### 任务 4：让 Pinia 正确管理增量消息、取消和重试

**文件：**

- 修改：`smrag-web/src/stores/chat.js`
- 创建：`smrag-web/src/stores/chat.spec.js`

- [ ] **步骤 1：写流状态测试**

测试以下状态机：

- `askQuestion` 立即插入 `{ question, answer: '', status: 'streaming' }`，delta 追加到同一对象。
- meta 更新 sessionId 和 routeType，citations 替换结构化引用，done 将状态改为 `complete`。
- error 将状态改为 `error` 并保存 client-safe message。
- 重复发送在 active request 期间被拒绝。
- `clearSession` 和 `cancelCurrent` 调用当前 `AbortController.abort()`，不保留半截 AI 回答；用户问题可以保留为 `cancelled` 以便重试。
- history 兼容旧记录的 `referenceDocs`，但新消息只使用 `citations`。

- [ ] **步骤 2：运行测试并确认失败**

```powershell
npm test -- --run src/stores/chat.spec.js
```

- [ ] **步骤 3：实现状态机**

Store 暴露：`currentSessionId`、`chatHistory`、`streaming`、`askQuestion`、`cancelCurrent`、`retryMessage`、`loadHistory`、`clearSession`。sessionId 优先使用 `crypto.randomUUID()`，不支持时再回退时间戳。每条消息用独立 `id`，Vue key 不再使用数组 index。

错误处理不 `console.error` 后静默；store 保存 `errorMessage`，页面负责展示并提供重试。`finally` 中清空 active controller，确保发送按钮恢复。

- [ ] **步骤 4：运行测试并提交**

```powershell
npm test -- --run src/stores/chat.spec.js
git add src/stores/chat.js src/stores/chat.spec.js
git commit -m "feat: manage streaming chat state"
```

### 任务 5：把 Chat.vue 重排为企业知识工作台

**文件：**

- 修改：`smrag-web/src/views/Chat.vue`
- 创建：`smrag-web/src/views/Chat.spec.js`

- [ ] **步骤 1：写交互与可访问性测试**

使用 Vue Test Utils 挂载页面，断言：空白问题不可发送；Enter 发送、Shift+Enter 换行；流式期间显示停止按钮；错误消息有 `role="alert"`；引用按钮可用键盘展开；每个 icon-only 按钮有 `aria-label` 和 tooltip；`MANAGER_QUERY` 有中文标签；完成后焦点回到输入框。

- [ ] **步骤 2：定义排版与视觉令牌**

在 Chat.vue scoped style 中使用以下局部令牌，后续全站设计系统抽取另立任务，不在本迭代改写所有页面：

```css
.chat-page {
  --chat-canvas: oklch(0.97 0.006 220);
  --chat-panel: oklch(0.995 0.004 220);
  --chat-panel-muted: oklch(0.94 0.01 220);
  --chat-text: oklch(0.24 0.018 240);
  --chat-muted: oklch(0.48 0.016 240);
  --chat-border: oklch(0.86 0.012 225);
  --chat-accent: oklch(0.50 0.12 165);
  --chat-accent-soft: oklch(0.92 0.04 165);
  --chat-danger: oklch(0.55 0.18 25);
}
```

页面使用 `apple-design` 建议的系统字体栈、固定 rem 字号和 1px 边框。移除整页渐变、发光动画、漂浮动画、圆形装饰和紫橙混色；不使用 nested card、玻璃拟态或超大标题。按下态在 pointer-down 时立即反馈，普通状态过渡控制在 100 到 200ms，只动画 opacity/transform；不为高频流式文本添加动画。所有 motion 必须可中断，并为 `prefers-reduced-motion` 提供无位移版本。

- [ ] **步骤 3：实现三段式工作台布局**

- 顶部工具栏：紧凑标题“智能问答”、当前会话状态、icon-only 清空按钮（Element Plus `Delete` + tooltip）。
- 中部消息流：用户消息右对齐且最大 70ch；AI 回答左对齐并占主要阅读宽度；流式光标不改变布局；加载使用稳定高度骨架，不使用三点弹跳。
- 底部输入区：`el-input type="textarea"`，固定最小和最大高度；右侧发送/停止 icon 按钮使用 `Promotion`/`VideoPause`；快速问题改为一行可横向滚动的轻量 action，不堆叠成卡片。

所有手绘 SVG 替换为已安装的 Element Plus Icons。文案使用 UTF-8 正常中文，修复当前文件的乱码和不闭合标签。

- [ ] **步骤 4：实现结构化引用展示**

每条 AI 回答下方显示“来源 N”按钮；展开后使用无外层卡片的列表，每项显示文档名、页码、相关度百分比和不超过三行的 excerpt。来源按 vectorId 作为 key。没有 citations 时不渲染空容器，业务工具路由显示轻量 route tag。

在 375px 下：标题栏不换出视口、消息宽度不超过容器、输入按钮保持 40px 点击区域、来源元数据自动换行；在 1440px 下内容最大宽度 1120px，下一层内容始终可见。

- [ ] **步骤 5：运行组件测试和构建**

```powershell
npm test -- --run src/views/Chat.spec.js
npm run build
```

- [ ] **步骤 6：浏览器视觉验证**

启动前端后，在 1440x900、768x1024、375x812 三个 viewport 检查：无横向滚动；标题、路由标签、消息、引用和输入区不重叠；长文档名可换行；流式更新不导致输入区跳动；键盘 focus ring 清晰。保存截图供本任务验收，不把截图加入生产资源目录。

- [ ] **步骤 7：提交**

```powershell
git add src/views/Chat.vue src/views/Chat.spec.js
git commit -m "feat: redesign streaming rag workspace"
```

### 任务 6：增加安全的 Actuator、Micrometer 和 RAG 指标

**文件：**

- 修改：`smrag-core/pom.xml`
- 修改：`smrag-core/src/main/resources/application.yml`
- 创建：`smrag-core/src/main/java/com/hqc/smrag/observation/RagMetrics.java`
- 创建：`smrag-core/src/test/java/com/hqc/smrag/observation/RagMetricsTest.java`

- [ ] **步骤 1：写指标标签基数测试**

使用 `SimpleMeterRegistry`，记录不同 sessionId、问题和文档名，断言 meter tag 只包含低基数的 `operation`、`route`、`outcome`、`provider`；禁止将 sessionId、userId、question、prompt、tool arguments、documentName 放入 tag。

- [ ] **步骤 2：实现指标门面**

`RagMetrics` 提供 timer/counter：`smrag.ai.chat.duration`、`smrag.ai.chat.errors`、`smrag.rag.retrieval.duration`、`smrag.rag.documents`、`smrag.ingestion.duration`、`smrag.ingestion.errors`。在编排、检索和入库服务边界记录，不在每个 chunk 上创建新 meter。

- [ ] **步骤 3：配置生产安全端点**

POM 增加 `spring-boot-starter-actuator` 和 `micrometer-registry-prometheus`。配置只暴露 `health,info,prometheus`；health details 默认 never；打开 Spring AI observations，但保持 `spring.ai.chat.observations.log-prompt=false` 和 `spring.ai.vectorstore.observations.log-query-response=false`。应用日志级别由 DEBUG 调整为 INFO，异常日志保留 correlation id。

- [ ] **步骤 4：运行测试并提交**

```powershell
mvn -pl smrag-core -Dtest=RagMetricsTest test
git add smrag-core/pom.xml smrag-core/src/main/resources/application.yml smrag-core/src/main/java/com/hqc/smrag/observation/RagMetrics.java smrag-core/src/test/java/com/hqc/smrag/observation/RagMetricsTest.java
git commit -m "feat: add safe rag observability"
```

### 任务 7：建立显式启用的 RAG 相关性评测

**文件：**

- 创建：`smrag-core/src/test/resources/application-evaluation.yml`
- 创建：`smrag-core/src/test/resources/evaluation/rag-cases.json`
- 创建：`smrag-core/src/test/java/com/hqc/smrag/evaluation/RagRelevancyEvaluationTest.java`

- [ ] **步骤 1：建立固定评测集**

JSON 至少包含 12 个已核对问题：产品风险、最低金额、经理信息、定义型知识、跨页 PDF 信息、无答案问题。每项字段固定为 `id`、`question`、字符串 `knowledgeBaseId`、`expectedDocumentIds`、`mustContain`、`expectRefusal`。数据仅使用仓库可分发的测试资料，不包含真实客户信息。

- [ ] **步骤 2：写默认跳过、显式执行的测试**

测试使用 `@EnabledIfEnvironmentVariable(named = "RUN_RAG_EVAL", matches = "true")` 和 `@ActiveProfiles("evaluation")`。先断言检索 topK 命中率，再用 Spring AI `RelevancyEvaluator` 评估答案是否有上下文支撑。阈值：检索 recall@8 不低于 0.80；有答案样例相关性通过率不低于 0.75；无答案样例全部拒答。

- [ ] **步骤 3：配置独立评测 profile**

`application-evaluation.yml` 允许连接本地测试数据库和模型，但 key 仍只从环境变量读取。普通 `mvn test` 不调用外网模型；显式运行命令：

```powershell
$env:RUN_RAG_EVAL='true'
if (-not $env:DASHSCOPE_API_KEY) { throw '请先在当前终端设置 DASHSCOPE_API_KEY' }
mvn -pl smrag-core -Dtest=RagRelevancyEvaluationTest test
```

密钥只通过当前终端环境变量传入，不写入任何文件、计划执行日志或 Git 历史。

- [ ] **步骤 4：提交评测资产**

```powershell
git add smrag-core/src/test/resources/application-evaluation.yml smrag-core/src/test/resources/evaluation/rag-cases.json smrag-core/src/test/java/com/hqc/smrag/evaluation/RagRelevancyEvaluationTest.java
git commit -m "test: add opt in rag relevance evaluation"
```

### 任务 8：端到端验收与交付

- [ ] **步骤 1：运行后端全量验证**

```powershell
$env:JAVA_HOME='D:\JavaProject\development\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
mvn -pl smrag-core test
mvn -pl smrag-core package -DskipTests
```

JDK 21 仅作为当前机器的临时编译器，必须确认 Maven Compiler 输出使用 `--release 17`。发布前再使用真实 JDK 17 重跑同样命令。

- [ ] **步骤 2：运行前端全量验证**

```powershell
Set-Location smrag-web
npm test
npm run build
```

- [ ] **步骤 3：运行完整应用冒烟**

验证同步和流式接口都能回答六类路由；断开流后数据库没有半截答案；知识问答 citations 与答案对应；空知识拒答；连续追问使用历史；清空后历史隔离；Prometheus 端点存在模型、检索和入库聚合指标且不包含问题文本。

- [ ] **步骤 4：执行浏览器验收**

使用浏览器开发工具验证 SSE event 顺序和 cancel；执行三个 viewport 截图；检查键盘操作、焦点、错误重试、长答案、长文档名、空状态和 reduced-motion。确认页面没有渐变装饰、嵌套卡片、圆形文字按钮、手绘 SVG 和移动端遮挡。

- [ ] **步骤 5：检查变更边界**

```powershell
git diff --check
git status --short
git log --oneline -8
```

最终报告分别列出后端测试数、前端测试数、构建结果、浏览器 viewport 和未执行的真实模型评测。不得把“评测默认跳过”描述成“评测通过”。
