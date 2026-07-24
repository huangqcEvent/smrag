# 开放问答路由实现计划

> **面向 AI 代理的工作者：** 必需子技能：在当前会话使用 `superpowers:executing-plans` 逐任务实现本计划。每个行为变更严格执行测试先失败、最小实现、测试转绿的循环。

**目标：** 新增不调用知识库或业务工具的 `GENERAL_CHAT` 路由，使开放性问题支持同步与 SSE 流式直接回答。

**架构：** 保留现有结构化路由器和 `AiChatService` 编排边界，新增独立 `GeneralChatService`。路由器只负责分类；新服务只使用基础 `ChatClient` 和隔离会话历史；统一编排层继续负责空答案校验、持久化、超时、安全错误和 SSE 事件。

**技术栈：** JDK 17、Spring Boot 3.5.8、Spring AI 1.1.2、Reactor、JUnit 5、AssertJ、Mockito、Maven。

---

## 文件结构

- 修改：`smrag-core/src/main/java/com/hqc/smrag/enums/RouteType.java`

  新增开放问答路由枚举值及说明。
- 修改：`smrag-core/src/main/resources/prompts/structured_route_prompt.txt`

  明确 `KNOWLEDGE_QA` 与 `GENERAL_CHAT` 的互斥分类边界。
- 修改：`smrag-core/src/test/java/com/hqc/smrag/service/chat/RouteServiceTest.java`

  验证结构化路由保留 `GENERAL_CHAT`，同时保留失败回退行为。
- 创建：`smrag-core/src/test/java/com/hqc/smrag/service/chat/RoutePromptContractTest.java`

  锁定路由提示词的分类契约。
- 创建：`smrag-core/src/main/java/com/hqc/smrag/service/chat/GeneralChatService.java`

  封装不带工具和 RAG advisor 的同步/流式直接回答。
- 创建：`smrag-core/src/test/java/com/hqc/smrag/service/chat/GeneralChatServiceTest.java`

  验证系统约束、历史传递、同步/流式调用和异常包装。
- 修改：`smrag-core/src/main/java/com/hqc/smrag/service/chat/AiChatService.java`

  注入新服务并在同步、流式、阶段映射中分发 `GENERAL_CHAT`。
- 修改：`smrag-core/src/test/java/com/hqc/smrag/service/chat/AiChatServiceTest.java`

  验证同步和流式开放问答的分支隔离、响应、事件与持久化。
- 修改：`smrag-core/src/test/java/com/hqc/smrag/service/chat/AiChatServiceWiringTest.java`

  锁定 Spring 注入构造器包含 `GeneralChatService`。

### 任务 1：建立开放问答路由契约

**文件：**

- 修改：`smrag-core/src/test/java/com/hqc/smrag/service/chat/RouteServiceTest.java`
- 创建：`smrag-core/src/test/java/com/hqc/smrag/service/chat/RoutePromptContractTest.java`
- 修改：`smrag-core/src/main/java/com/hqc/smrag/enums/RouteType.java`
- 修改：`smrag-core/src/main/resources/prompts/structured_route_prompt.txt`

- [x] **步骤 1：编写失败的结构化路由测试**

在 `RouteServiceTest` 增加：

```java
@Test
void preservesStructuredGeneralChatRoute() {
    when(decisionClient.decide("解释一下什么是复利"))
        .thenReturn(new RouteDecision(RouteType.GENERAL_CHAT, "general explanation"));

    assertThat(routeService.route("解释一下什么是复利"))
        .isEqualTo(RouteType.GENERAL_CHAT);
}
```

创建 `RoutePromptContractTest`，读取真实 classpath 提示词：

```java
@Test
void separatesPrivateKnowledgeFromGeneralQuestions() throws IOException {
    String prompt = new ClassPathResource(
        "prompts/structured_route_prompt.txt"
    ).getContentAsString(StandardCharsets.UTF_8);

    assertThat(prompt)
        .contains("GENERAL_CHAT")
        .contains("private", "uploaded", "general");
}
```

- [x] **步骤 2：运行测试并确认红灯**

运行：

```powershell
mvn -pl smrag-core -Dtest=RouteServiceTest,RoutePromptContractTest test
```

预期：编译失败，指出 `RouteType.GENERAL_CHAT` 不存在；这是功能缺失导致的预期红灯。

- [x] **步骤 3：添加最小路由实现**

在 `RouteType` 增加：

```java
GENERAL_CHAT("开放问答")
```

在路由提示词允许值中加入：

```text
- KNOWLEDGE_QA: requests that require private policies, uploaded documents,
  selected knowledge bases, or other internal evidence.
- GENERAL_CHAT: general explanations, writing, brainstorming, or conversation
  that can be answered without private knowledge or business tools.
```

保留 `RouteDecision` 和 `RouteService` 的 `KNOWLEDGE_QA` 默认值。
由于 Java 枚举 switch 必须穷尽所有值，先在 `AiChatService` 的同步和流式
switch 中为 `GENERAL_CHAT` 增加显式未实现保护，并将其阶段标记为
`GENERATING`；任务 3 的行为测试负责将保护替换为真实服务分发。

- [x] **步骤 4：运行路由测试并确认绿灯**

运行：

```powershell
mvn -pl smrag-core -Dtest=RouteServiceTest,RoutePromptContractTest test
```

预期：两个测试类全部通过。

- [x] **步骤 5：提交路由契约**

```powershell
git add docs/superpowers/plans/2026-07-24-general-chat-route.md smrag-core/src/main/java/com/hqc/smrag/enums/RouteType.java smrag-core/src/main/java/com/hqc/smrag/service/chat/AiChatService.java smrag-core/src/main/resources/prompts/structured_route_prompt.txt smrag-core/src/test/java/com/hqc/smrag/service/chat/RouteServiceTest.java smrag-core/src/test/java/com/hqc/smrag/service/chat/RoutePromptContractTest.java
git commit -m "feat: 添加开放问答路由类型（任务 1/4）"
```

### 任务 2：实现无工具的开放问答服务

**文件：**

- 创建：`smrag-core/src/test/java/com/hqc/smrag/service/chat/GeneralChatServiceTest.java`
- 创建：`smrag-core/src/main/java/com/hqc/smrag/service/chat/GeneralChatService.java`

- [ ] **步骤 1：编写失败的服务测试**

测试期望的构造器和 API：

```java
GeneralChatService service = new GeneralChatService(chatClient, chatMemory);

assertThat(GeneralChatService.systemPrompt())
    .contains("user's language")
    .contains("no access")
    .contains("real-time")
    .contains("never promise returns");
```

流式行为使用真实 `Flux` 验证：

```java
when(chatClient.prompt()).thenReturn(requestSpec);
when(requestSpec.system(any(String.class))).thenReturn(requestSpec);
when(requestSpec.messages(any(List.class))).thenReturn(requestSpec);
when(requestSpec.user("解释复利")).thenReturn(requestSpec);
when(requestSpec.stream()).thenReturn(streamSpec);
when(streamSpec.content()).thenReturn(Flux.just("复利是", "利息再投资"));
when(chatMemory.get("7:s1")).thenReturn(List.of(new UserMessage("上一问")));

StepVerifier.create(service.stream("解释复利", "7:s1"))
    .expectNext("复利是", "利息再投资")
    .verifyComplete();

verify(requestSpec).messages(chatMemory.get("7:s1"));
verify(requestSpec, never()).toolCallbacks(any(ToolCallback[].class));
```

另一个测试让 `streamSpec.content()` 返回 `Flux.error(...)`，断言最终异常为 `AiServiceException`。

- [ ] **步骤 2：运行测试并确认红灯**

运行：

```powershell
mvn -pl smrag-core -Dtest=GeneralChatServiceTest test
```

预期：编译失败，指出 `GeneralChatService` 不存在。

- [ ] **步骤 3：实现最小服务**

新增 `@Service`：

```java
@Service
public class GeneralChatService {

    private static final String SYSTEM_PROMPT = """
        You are FundRAG's general-purpose assistant.
        Answer directly in the user's language.
        You have no access to private knowledge bases, business databases,
        external tools, the internet, or real-time data. Never claim otherwise.
        State uncertainty when facts may have changed, and never invent citations,
        fund products, or manager details.
        For investments, provide general educational information and never promise
        returns or present uncertain personalized advice as fact.
        """;

    private final ChatClient chatClient;
    private final ChatMemory chatMemory;

    public String answer(String question, String conversationKey) {
        try {
            return chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .messages(history(conversationKey))
                .user(question)
                .call()
                .content();
        } catch (RuntimeException failure) {
            throw new AiServiceException("General chat AI request failed", failure);
        }
    }

    public Flux<String> stream(String question, String conversationKey) {
        return Flux.defer(() -> chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .messages(history(conversationKey))
                .user(question)
                .stream()
                .content())
            .onErrorMap(
                failure -> !(failure instanceof AiServiceException),
                failure -> new AiServiceException("General chat AI request failed", failure)
            );
    }
}
```

生产构造器接收 `ChatClient.Builder` 与 `ChatMemory`，包级构造器接收 `ChatClient` 便于单元测试；`history()` 将空值规范为 `List.of()`。

- [ ] **步骤 4：运行服务测试并确认绿灯**

运行：

```powershell
mvn -pl smrag-core -Dtest=GeneralChatServiceTest test
```

预期：全部通过，流式异常被包装为 `AiServiceException`。

- [ ] **步骤 5：提交开放问答服务**

```powershell
git add smrag-core/src/main/java/com/hqc/smrag/service/chat/GeneralChatService.java smrag-core/src/test/java/com/hqc/smrag/service/chat/GeneralChatServiceTest.java
git commit -m "feat: 添加开放问答服务"
```

### 任务 3：接入同步与流式聊天编排

**文件：**

- 修改：`smrag-core/src/test/java/com/hqc/smrag/service/chat/AiChatServiceTest.java`
- 修改：`smrag-core/src/test/java/com/hqc/smrag/service/chat/AiChatServiceWiringTest.java`
- 修改：`smrag-core/src/main/java/com/hqc/smrag/service/chat/AiChatService.java`

- [ ] **步骤 1：编写失败的同步分发测试**

在测试 fixture 中增加 `GeneralChatService general` mock 并注入 `AiChatService`。新增：

```java
@Test
void answersGeneralQuestionWithoutKnowledgeOrBusinessServices() {
    Fixture fixture = new Fixture();
    when(fixture.query.rewrite("原问题", "7:s1")).thenReturn("解释复利");
    when(fixture.router.route("解释复利")).thenReturn(RouteType.GENERAL_CHAT);
    when(fixture.general.answer("解释复利", "7:s1")).thenReturn("通识回答");

    ChatResponseDTO response = fixture.service.ask(request());

    assertThat(response.getRouteType()).isEqualTo("GENERAL_CHAT");
    assertThat(response.getAnswer()).isEqualTo("通识回答");
    assertThat(response.getCitations()).isEmpty();
    verify(fixture.knowledge, never()).answer(any(), any(), any(), any(), any());
    verify(fixture.business, never()).answer(any(), any(), any());
    verify(fixture.sales, never()).generate(any(), any(), any());
}
```

在 `AiChatServiceWiringTest` 断言注入构造器包含 `GeneralChatService.class`。

- [ ] **步骤 2：运行测试并确认红灯**

运行：

```powershell
mvn -pl smrag-core -Dtest=AiChatServiceTest,AiChatServiceWiringTest test
```

预期：编译失败或断言失败，因为 `AiChatService` 尚未接收和分发 `GeneralChatService`。

- [ ] **步骤 3：实现同步最小分发**

在 `AiChatService`：

```java
private final GeneralChatService generalChatService;
```

同步 switch 增加：

```java
case GENERAL_CHAT -> new BranchAnswer(
    generalChatService.answer(question, sessionId),
    List.of()
);
```

更新兼容构造器参数及其委托调用，保持唯一 `@Autowired` 构造器。

- [ ] **步骤 4：运行同步测试并确认绿灯**

运行：

```powershell
mvn -pl smrag-core -Dtest=AiChatServiceTest,AiChatServiceWiringTest test
```

预期：同步开放问答测试和构造器测试通过。

- [ ] **步骤 5：编写失败的 SSE 分发测试**

新增：

```java
@Test
void streamsGeneralAnswerWithoutCitationEvent() {
    Fixture fixture = new Fixture();
    when(fixture.query.rewrite("原问题", "7:s1")).thenReturn("解释复利");
    when(fixture.router.route("解释复利")).thenReturn(RouteType.GENERAL_CHAT);
    when(fixture.general.stream("解释复利", "7:s1"))
        .thenReturn(Flux.just("通识", "回答"));

    StepVerifier.create(fixture.service.stream(request()))
        .recordWith(ArrayList::new)
        .thenConsumeWhile(event -> true)
        .consumeRecordedWith(events -> {
            assertThat(events).extracting(ChatStreamEvent::type)
                .contains("status", "metadata", "delta", "done")
                .doesNotContain("citations");
            assertThat(events.stream()
                .filter(event -> "status".equals(event.type()))
                .map(ChatStreamEvent::phase))
                .contains("GENERATING");
        })
        .verifyComplete();
}
```

- [ ] **步骤 6：运行测试并确认红灯**

运行：

```powershell
mvn -pl smrag-core -Dtest=AiChatServiceTest#streamsGeneralAnswerWithoutCitationEvent test
```

预期：测试失败，因为流式 switch 和阶段映射未处理 `GENERAL_CHAT`。

- [ ] **步骤 7：实现流式最小分发**

在 `streamBranch(...)` 增加：

```java
case GENERAL_CHAT -> streamText(
    generalChatService.stream(
        context.rewrittenQuestion(),
        context.request().conversationKey()
    ),
    accumulator
);
```

在 `phase(...)` 增加：

```java
case GENERAL_CHAT, SALES_SCRIPT -> "GENERATING";
```

保持 `citationsAfterGeneration(...)` 只允许 `KNOWLEDGE_QA`。

- [ ] **步骤 8：运行编排测试并确认绿灯**

运行：

```powershell
mvn -pl smrag-core -Dtest=AiChatServiceTest,AiChatServiceWiringTest test
```

预期：同步和流式开放问答、现有 RAG 分支测试全部通过。

- [ ] **步骤 9：提交聊天编排**

```powershell
git add smrag-core/src/main/java/com/hqc/smrag/service/chat/AiChatService.java smrag-core/src/test/java/com/hqc/smrag/service/chat/AiChatServiceTest.java smrag-core/src/test/java/com/hqc/smrag/service/chat/AiChatServiceWiringTest.java
git commit -m "feat: 接入开放问答聊天分支"
```

### 任务 4：回归验证与规格核对

**文件：**

- 检查：`docs/superpowers/specs/2026-07-24-general-chat-route-design.md`
- 检查：本计划列出的所有生产和测试文件

- [ ] **步骤 1：运行聊天模块定向回归**

运行：

```powershell
mvn -pl smrag-core -Dtest=RouteServiceTest,RoutePromptContractTest,GeneralChatServiceTest,AiChatServiceTest,AiChatServiceWiringTest,BusinessToolChatServiceTest,KnowledgeQaServiceTest,SalesScriptServiceTest,ChatControllerTest test
```

预期：全部测试通过，失败数为 0。

- [ ] **步骤 2：运行完整核心模块测试**

运行：

```powershell
mvn -pl smrag-core test
```

预期：Maven `BUILD SUCCESS`，测试失败数和错误数均为 0。

- [ ] **步骤 3：检查差异和路由覆盖**

运行：

```powershell
git diff --check
rg -n "GENERAL_CHAT" smrag-core/src/main smrag-core/src/test
```

预期：`git diff --check` 无错误；枚举、提示词、同步分发、流式分发、阶段映射和测试均包含 `GENERAL_CHAT`。

- [ ] **步骤 4：逐项核对规格**

确认：

- 开放问答没有工具回调或 RAG advisor。
- `KNOWLEDGE_QA` 的默认回退未变。
- 一般回答的引用列表为空且 SSE 不发送 `citations`。
- 同步和流式成功后才使用现有持久化服务保存会话。
- API、DTO、数据库和前端无需修改。

- [ ] **步骤 5：请求代码审查**

以设计提交 `3ef3e50` 为基线，审查本计划实现的所有差异；立即修复 Critical 和 Important 问题，并重新运行受影响测试。

- [ ] **步骤 6：提交验证修正**

如果代码审查产生修正：

```powershell
git add -- smrag-core/src/main/java/com/hqc/smrag smrag-core/src/main/resources/prompts smrag-core/src/test/java/com/hqc/smrag
git commit -m "fix: 完善开放问答路由实现"
```

若无修正则不创建空提交。
