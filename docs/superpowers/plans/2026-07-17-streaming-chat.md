# Streaming Chat Implementation Plan

> **For AI agent workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task by task. Track progress with `- [ ]` checkboxes.

**Goal:** Deliver end-to-end POST SSE chat streaming through Spring AI while preserving synchronous chat, structured routing, RAG citations, restricted tools, memory, and successful-completion persistence.

**Architecture:** Keep Spring MVC as the application web stack and add WebFlux for Spring AI's reactive model API. `AiChatService` performs blocking rewrite and routing on Reactor's bounded-elastic scheduler, selects a route-specific `Flux`, emits typed SSE events, aggregates deltas, and persists only after normal completion. The Vue client uses fetch and a tested SSE parser to update one assistant message in place and cancel with `AbortController`.

**Tech Stack:** JDK 17, Spring Boot 3.5.8, Spring AI 1.1.2, Spring AI Alibaba DashScope 1.1.2.1, Reactor, Spring MVC SSE, JUnit 5, Mockito, Reactor Test, Vue 3, Pinia, browser Fetch/ReadableStream, Node test runner.

---

## File Structure

### Backend

- Modify `smrag-core/pom.xml`: add the WebFlux reactive stack required by Spring AI streaming.
- Create `smrag-core/src/main/java/com/hqc/smrag/dto/ChatStreamEvent.java`: immutable event payload and named factory methods.
- Create `smrag-core/src/main/java/com/hqc/smrag/config/ChatStreamProperties.java`: configurable generation timeout.
- Modify `smrag-core/src/main/resources/application.yml`: bind the stream timeout.
- Modify `smrag-core/src/main/java/com/hqc/smrag/controller/ChatController.java`: expose POST SSE without changing `/ask`.
- Modify `smrag-core/src/main/java/com/hqc/smrag/service/chat/AiChatService.java`: share request preparation and add stream orchestration, aggregation, cancellation, sanitization, and completion persistence.
- Modify `smrag-core/src/main/java/com/hqc/smrag/service/chat/KnowledgeChatClient.java`: add the reactive knowledge-answer contract.
- Modify `smrag-core/src/main/java/com/hqc/smrag/service/chat/SpringAiKnowledgeChatClient.java`: stream `ChatClientResponse` and advisor document context.
- Modify `smrag-core/src/main/java/com/hqc/smrag/service/chat/KnowledgeQaService.java`: stream answer chunks and citations without a second retrieval.
- Modify `smrag-core/src/main/java/com/hqc/smrag/service/chat/BusinessToolChatService.java`: stream restricted tool-backed answers.
- Modify `smrag-core/src/main/java/com/hqc/smrag/service/chat/SalesScriptService.java`: stream compliant sales answers.
- Modify `smrag-core/src/test/java/com/hqc/smrag/controller/ChatControllerTest.java`: verify the HTTP SSE contract and synchronous regression.
- Modify `smrag-core/src/test/java/com/hqc/smrag/service/chat/AiChatServiceTest.java`: verify event order, aggregation, persistence, failure, and cancellation.
- Modify route service tests: verify reactive delegation and error mapping.

### Frontend

- Create `smrag-web/src/utils/sse.js`: incremental SSE parser and stream consumer.
- Create `smrag-web/tests/sse.test.js`: network-boundary and Unicode parser tests.
- Modify `smrag-web/package.json`: add the Node test command.
- Modify `smrag-web/src/api/chat.js`: add fetch-based POST streaming with `AbortSignal`.
- Modify `smrag-web/src/stores/chat.js`: insert and update one pending assistant message, expose stop state, and finalize metadata.
- Modify `smrag-web/src/views/Chat.vue`: render route-aware status and streamed text, replace send with stop, and throttle scrolling.

### Documentation

- Modify `smrag-web/README.md`: document `/api/chat/stream` and the event contract.

---

### Task 1: Define the HTTP SSE Contract

**Files:**
- Modify: `smrag-core/pom.xml`
- Create: `smrag-core/src/main/java/com/hqc/smrag/dto/ChatStreamEvent.java`
- Modify: `smrag-core/src/main/java/com/hqc/smrag/controller/ChatController.java`
- Test: `smrag-core/src/test/java/com/hqc/smrag/controller/ChatControllerTest.java`

- [ ] **Step 1: Write the failing controller test**

Add a finite mocked stream and assert async SSE output contains ordered named events while `/ask` remains JSON:

```java
when(aiChatService.stream(any())).thenReturn(Flux.just(
    ChatStreamEvent.status("ANALYZING"),
    ChatStreamEvent.metadata("session-1", "KNOWLEDGE_QA"),
    ChatStreamEvent.delta("Hello"),
    ChatStreamEvent.done("session-1", "KNOWLEDGE_QA")
));

MvcResult result = mockMvc.perform(post("/api/chat/stream")
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.TEXT_EVENT_STREAM)
        .content(objectMapper.writeValueAsString(request)))
    .andExpect(request().asyncStarted())
    .andReturn();

mockMvc.perform(asyncDispatch(result))
    .andExpect(status().isOk())
    .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
    .andExpect(content().string(containsString("event:delta")));
```

- [ ] **Step 2: Run the test and verify RED**

Run:

```powershell
$env:JAVA_HOME='D:\JavaProject'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
mvn -pl smrag-core -Dtest=ChatControllerTest test
```

Expected: compilation failure because `ChatStreamEvent`, `AiChatService.stream`, and `/stream` do not exist.

- [ ] **Step 3: Add the minimum event DTO and controller endpoint**

Use one immutable DTO with explicit factories so null-bearing wire fields remain controlled:

```java
public record ChatStreamEvent(
    String type,
    String phase,
    String sessionId,
    String routeType,
    String content,
    List<CitationDTO> citations,
    List<String> references,
    Integer code,
    String message
) {
    public static ChatStreamEvent delta(String content) {
        return new ChatStreamEvent(
            "delta", null, null, null, content, List.of(), List.of(), null, null
        );
    }

    public static ChatStreamEvent error(int code, String message) {
        return new ChatStreamEvent(
            "error", null, null, null, null, List.of(), List.of(), code, message
        );
    }
}
```

Map each domain event to `ServerSentEvent.builder(event).event(event.type()).build()` and declare:

```java
@PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<ServerSentEvent<ChatStreamEvent>> stream(
    @Valid @RequestBody ChatRequestDTO request
) {
    return aiChatService.stream(request)
        .map(event -> ServerSentEvent.builder(event).event(event.type()).build());
}
```

Add `spring-boot-starter-webflux` without removing `spring-boot-starter-web`.

- [ ] **Step 4: Run the controller test and verify GREEN**

Expected: SSE test and all existing `ChatControllerTest` methods pass.

- [ ] **Step 5: Commit the contract slice**

```powershell
git add smrag-core/pom.xml smrag-core/src/main/java/com/hqc/smrag/dto/ChatStreamEvent.java smrag-core/src/main/java/com/hqc/smrag/controller/ChatController.java smrag-core/src/test/java/com/hqc/smrag/controller/ChatControllerTest.java
git commit -m "feat: add chat SSE contract"
```

### Task 2: Add Stream Orchestration and Completion Persistence

**Files:**
- Create: `smrag-core/src/main/java/com/hqc/smrag/config/ChatStreamProperties.java`
- Modify: `smrag-core/src/main/resources/application.yml`
- Modify: `smrag-core/src/main/java/com/hqc/smrag/service/chat/AiChatService.java`
- Test: `smrag-core/src/test/java/com/hqc/smrag/service/chat/AiChatServiceTest.java`

- [ ] **Step 1: Write failing service tests**

Cover these behaviors with `StepVerifier`:

```java
StepVerifier.create(service.stream(request))
    .assertNext(event -> assertThat(event.phase()).isEqualTo("ANALYZING"))
    .assertNext(event -> assertThat(event.routeType()).isEqualTo("KNOWLEDGE_QA"))
    .assertNext(event -> assertThat(event.content()).isEqualTo("Grounded "))
    .assertNext(event -> assertThat(event.content()).isEqualTo("answer"))
    .assertNext(event -> assertThat(event.type()).isEqualTo("citations"))
    .assertNext(event -> assertThat(event.type()).isEqualTo("done"))
    .verifyComplete();

verify(persistenceService).save(argThat(session ->
    session.getAnswer().equals("Grounded answer")
));
```

Also assert:

- Provider failure emits one sanitized `error` and never persists.
- Empty aggregate output emits `error` and never persists.
- Cancelling after the first delta never persists.
- The original question, rewritten question, route, and citations match synchronous persistence.

- [ ] **Step 2: Run `AiChatServiceTest` and verify RED**

Expected: failure because streaming collaborators and orchestration are absent.

- [ ] **Step 3: Extract shared request preparation**

Introduce focused private records inside `AiChatService`:

```java
private record NormalizedRequest(
    ChatRequestDTO source,
    String sessionId,
    String question,
    String knowledgeBaseId,
    Long documentId
) {}

private record PreparedRequest(
    NormalizedRequest request,
    String rewrittenQuestion,
    RouteType routeType
) {}
```

Make `ask()` and `stream()` use the same normalizer and persistence builder.

- [ ] **Step 4: Implement bounded blocking preparation and ordered events**

Start with `status(ANALYZING)`, then run rewrite and route on `Schedulers.boundedElastic()`. Emit `metadata` and `RETRIEVING`, `CALLING_TOOL`, or `GENERATING` before subscribing to the selected branch stream.

Aggregate serial deltas, retain the latest citation set, validate non-blank final text, persist once, then append `citations` and `done`. Apply the configured timeout and convert post-connection failures to a generic error event.

- [ ] **Step 5: Run service tests and verify GREEN**

Expected: all stream orchestration tests pass with no change to synchronous tests.

- [ ] **Step 6: Commit orchestration**

```powershell
git add smrag-core/src/main/java/com/hqc/smrag/config/ChatStreamProperties.java smrag-core/src/main/resources/application.yml smrag-core/src/main/java/com/hqc/smrag/service/chat/AiChatService.java smrag-core/src/test/java/com/hqc/smrag/service/chat/AiChatServiceTest.java
git commit -m "feat: orchestrate streaming chat"
```

### Task 3: Stream All Spring AI Answer Branches

**Files:**
- Modify: `smrag-core/src/main/java/com/hqc/smrag/service/chat/KnowledgeChatClient.java`
- Modify: `smrag-core/src/main/java/com/hqc/smrag/service/chat/SpringAiKnowledgeChatClient.java`
- Modify: `smrag-core/src/main/java/com/hqc/smrag/service/chat/KnowledgeQaService.java`
- Modify: `smrag-core/src/main/java/com/hqc/smrag/service/chat/BusinessToolChatService.java`
- Modify: `smrag-core/src/main/java/com/hqc/smrag/service/chat/SalesScriptService.java`
- Test: `smrag-core/src/test/java/com/hqc/smrag/service/chat/KnowledgeQaServiceTest.java`
- Test: `smrag-core/src/test/java/com/hqc/smrag/service/chat/BusinessToolChatServiceTest.java`
- Test: `smrag-core/src/test/java/com/hqc/smrag/service/chat/SalesScriptServiceTest.java`

- [ ] **Step 1: Write failing reactive branch tests**

Add tests proving:

- Knowledge chunks retain document context and use `CitationAssembler` once per distinct snapshot.
- No evidence returns the stable no-evidence text and no model text leak.
- Business streaming selects exactly the route's allowed `ToolCallback`.
- Sales streaming retains the compliance prompt and conversation ID.
- Runtime failures are mapped to `AiServiceException` during subscription.

- [ ] **Step 2: Run focused tests and verify RED**

```powershell
mvn -pl smrag-core -Dtest=KnowledgeQaServiceTest,BusinessToolChatServiceTest,SalesScriptServiceTest test
```

Expected: compilation or assertion failure because the stream methods do not exist.

- [ ] **Step 3: Implement knowledge streaming from one advisor execution**

Add:

```java
Flux<KnowledgeAnswer> stream(
    String question,
    String sessionId,
    String knowledgeBaseId,
    Long documentId
);
```

Use `.stream().chatClientResponse()`, map each non-null response to its output text plus `RetrievalAugmentationAdvisor.DOCUMENT_CONTEXT`, and preserve cancellation. Use `switchOnFirst` in `KnowledgeQaService` so an empty evidence context emits only `NO_EVIDENCE_RESPONSE`.

- [ ] **Step 4: Implement business and sales streaming**

Mirror existing prompts, memory advisors, and restricted tool selection, replacing only `.call().content()` with `.stream().content()`. Use `onErrorMap` so subscription-time failures become `AiServiceException`.

- [ ] **Step 5: Run focused and full backend tests**

```powershell
mvn -pl smrag-core test
```

Expected: all backend tests pass on JDK 17.

- [ ] **Step 6: Commit Spring AI streams**

```powershell
git add smrag-core/src/main/java/com/hqc/smrag/service/chat smrag-core/src/test/java/com/hqc/smrag/service/chat
git commit -m "feat: stream Spring AI answer branches"
```

### Task 4: Build and Test the Browser SSE Parser

**Files:**
- Create: `smrag-web/src/utils/sse.js`
- Create: `smrag-web/tests/sse.test.js`
- Modify: `smrag-web/package.json`
- Modify: `smrag-web/src/api/chat.js`

- [ ] **Step 1: Write parser tests first**

Use `node:test` and `node:assert/strict` to verify records split between every character, CRLF separators, comments, multi-line data, and a Chinese character split between UTF-8 byte chunks.

```javascript
test('parses SSE records across arbitrary chunks', () => {
  const events = []
  const parser = createSseParser(event => events.push(event))
  for (const chunk of chunks) parser.push(chunk)
  parser.finish()
  assert.deepEqual(events, [{ event: 'delta', data: '{"content":"hello"}' }])
})
```

- [ ] **Step 2: Run `npm run test` and verify RED**

Expected: missing module or missing parser export.

- [ ] **Step 3: Implement the parser and stream consumer**

The parser must buffer incomplete lines and records. The consumer must use one `TextDecoder`, call `decoder.decode(value, { stream: true })`, flush on completion, ignore comment records, and JSON-parse only in `chat.js` where errors can include event context.

- [ ] **Step 4: Add `streamChat`**

```javascript
export async function streamChat(data, { signal, onEvent }) {
  const response = await fetch('/api/chat/stream', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream' },
    body: JSON.stringify(data),
    signal
  })
  if (!response.ok || !response.body) {
    throw await toChatStreamError(response)
  }
  await consumeSseStream(response.body, rawEvent => {
    onEvent({ type: rawEvent.event, ...JSON.parse(rawEvent.data) })
  })
}
```

- [ ] **Step 5: Run parser tests and frontend build**

```powershell
npm.cmd run test
npm.cmd run build
```

Expected: parser tests pass and Vite production build succeeds.

- [ ] **Step 6: Commit the client transport**

```powershell
git add smrag-web/package.json smrag-web/src/utils/sse.js smrag-web/tests/sse.test.js smrag-web/src/api/chat.js
git commit -m "feat: add browser SSE chat client"
```

### Task 5: Render and Cancel Streaming Answers

**Files:**
- Modify: `smrag-web/src/stores/chat.js`
- Modify: `smrag-web/src/views/Chat.vue`
- Modify: `smrag-web/README.md`

- [ ] **Step 1: Define the store transition table before implementation**

Use one pending message object:

```javascript
{
  question,
  answer: '',
  routeType: '',
  citations: [],
  references: [],
  streamStatus: 'ANALYZING',
  streaming: true,
  streamError: ''
}
```

Map events deterministically: `status` updates phase, `metadata` sets route, `delta` appends, `citations` replaces evidence, `done` clears streaming, and `error` records a user-safe message.

- [ ] **Step 2: Implement Pinia stream lifecycle**

Store the active `AbortController`, expose `stopGeneration()`, reject concurrent sends, retain partial text on server error, remove an empty aborted assistant message, and always reset `loading` in `finally`.

- [ ] **Step 3: Implement the Apple-style interaction state**

Render the assistant container immediately. Before the first delta, show compact route-aware status in the answer position. While text arrives, show a restrained cursor without resizing the message. Replace the send icon with a stop icon and tooltip; keep the circular control dimensions stable.

Use `requestAnimationFrame` to coalesce scroll-to-bottom calls and respect `prefers-reduced-motion`.

- [ ] **Step 4: Update API documentation**

Add `/api/chat/stream`, event names, and cancellation behavior to `smrag-web/README.md`. Remove stale manager API documentation while touching the table because those endpoints were removed from the product.

- [ ] **Step 5: Run frontend verification**

```powershell
npm.cmd run test
npm.cmd run build
```

Expected: all parser tests and production build pass.

- [ ] **Step 6: Commit the streaming UI**

```powershell
git add smrag-web/src/stores/chat.js smrag-web/src/views/Chat.vue smrag-web/README.md
git commit -m "feat: render cancellable streaming answers"
```

### Task 6: Full Regression and Real DashScope Verification

**Files:**
- Modify only if verification exposes a defect.

- [ ] **Step 1: Run full automated verification**

```powershell
$env:JAVA_HOME='D:\JavaProject'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
mvn -pl smrag-core test
Set-Location smrag-web
npm.cmd run test
npm.cmd run build
```

Expected: zero backend test failures, all Node tests pass, and Vite exits 0.

- [ ] **Step 2: Repackage and restart the backend with JDK 17**

Read `DASHSCOPE_API_KEY` from the Windows user environment, stop only the verified current backend PID, package the executable JAR, start it hidden, and wait for `Started SmragApplication` plus port 8080.

- [ ] **Step 3: Verify event timing through Vite**

POST to `http://127.0.0.1:3000/api/chat/stream` and record event timestamps. Require:

- HTTP 200 and `text/event-stream`.
- `status` then `metadata` before answer completion.
- At least two non-empty `delta` events before `done` for a normal prompt.
- No `error` event.

- [ ] **Step 4: Verify persistence and cancellation**

Fetch `/api/chat/history/{sessionId}` after `done` and assert one complete answer. Start a second stream, abort after its first delta, then assert no application history row exists for the cancelled session.

- [ ] **Step 5: Verify UI in a real browser**

At desktop and mobile widths, check the status-to-answer transition, stable layout, automatic scrolling, stop control, citation expansion, long Chinese text wrapping, and reduced-motion behavior. Confirm no console errors or overlapping controls.

- [ ] **Step 6: Review the final diff and secrets**

```powershell
git diff --check
git status --short
git grep -n -I -E 'sk-[A-Za-z0-9_-]{20,}' -- . ':!docs/superpowers/plans'
```

Expected: no whitespace errors, only intended files changed, and no API key in tracked content.

- [ ] **Step 7: Commit any verification-only fixes**

```powershell
git add -u -- smrag-core smrag-web
git commit -m "fix: harden streaming chat integration"
```
