# Streaming Chat Design

## Status

The user approved the POST + Server-Sent Events approach in the project conversation. This specification narrows the previously approved RAG design to the missing end-to-end streaming behavior and is ready for implementation planning.

## Objective

Add true token streaming to the existing JDK 17 Spring AI chat flow without removing the synchronous API or weakening routing, RAG citations, tool restrictions, chat memory, persistence, and error sanitization.

The first visible response should arrive before the model completes. The frontend must render each text delta into one stable assistant message and let the user cancel an in-flight response.

## Non-Goals

- Replacing the existing `/api/chat/ask` endpoint.
- Moving the whole application from Spring MVC to a reactive database stack.
- Adding WebSocket infrastructure or bidirectional realtime collaboration.
- Streaming internal chain-of-thought, tool arguments, retrieved document text, or provider errors.
- Persisting partial answers after cancellation or failure.

## Selected Approach

The application adds a POST SSE endpoint and keeps the existing synchronous endpoint:

- `POST /api/chat/ask`: unchanged JSON request and response.
- `POST /api/chat/stream`: same request DTO, `text/event-stream` response.

POST is required because the request contains a validated JSON body and may later carry additional scope fields. Native `EventSource` only supports GET, so the frontend uses `fetch()` and reads `Response.body` as a `ReadableStream`.

Spring AI streaming requires the reactive stack even when the application remains a Servlet application. `spring-boot-starter-webflux` is added alongside `spring-boot-starter-web`; Spring MVC remains the selected web application type, while Spring AI and controller return values use Reactor `Flux` for streaming.

## SSE Contract

The stream uses named SSE events. Every `data` value is JSON encoded and never contains raw provider exceptions.

### Event Order

1. `status`: emitted immediately with phase `ANALYZING`.
2. `metadata`: emitted after rewrite and routing with `sessionId` and `routeType`.
3. `status`: route-specific phase such as `RETRIEVING`, `CALLING_TOOL`, or `GENERATING`.
4. `delta`: emitted zero or more times with generated text fragments.
5. `citations`: emitted once for RAG responses after advisor context is available; omitted or empty for other routes.
6. `done`: emitted exactly once after successful persistence.

If processing fails after the stream has started, the server emits one sanitized `error` event and terminates the stream without `done`. Request validation failures that occur before streaming use the existing HTTP 400 response.

Example:

```text
event: status
data: {"phase":"ANALYZING"}

event: metadata
data: {"sessionId":"session-1","routeType":"KNOWLEDGE_QA"}

event: delta
data: {"content":"The policy "}

event: delta
data: {"content":"requires ..."}

event: citations
data: {"citations":[...],"references":["policy.pdf"]}

event: done
data: {"sessionId":"session-1","routeType":"KNOWLEDGE_QA"}
```

## Backend Architecture

`ChatController` delegates streaming requests to `AiChatService.stream(request)` and maps domain events to `ServerSentEvent<ChatStreamEvent>`.

The orchestration is split into preparation and generation:

1. Validate and normalize the request using the same rules as `ask()`.
2. Run conversation rewrite and structured route selection in `Mono.fromCallable(...).subscribeOn(Schedulers.boundedElastic())` because those existing operations are blocking.
3. Select a route-specific `Flux` without blocking the request thread.
4. Aggregate emitted answer deltas in stream order for final validation and persistence.
5. Persist the existing `ChatSession` only after normal model completion.
6. Emit citations and `done`, then close the response.

The synchronous and streaming flows share request normalization, route preparation, response construction, and persistence helpers so the two APIs cannot drift in validation or audit behavior.

## Route Behavior

### Knowledge RAG

`SpringAiKnowledgeChatClient` adds a streaming method based on:

```java
chatClient.prompt()
    .system(SYSTEM_PROMPT)
    .user(question)
    .advisors(memoryAdvisor, ragAdvisor)
    .stream()
    .chatClientResponse();
```

Each `ChatClientResponse` contributes an answer delta. Retrieved documents are captured from `RetrievalAugmentationAdvisor.DOCUMENT_CONTEXT`, deduplicated by Spring AI document ID, and converted by the existing `CitationAssembler`. Answer text and citations therefore come from one advisor execution; no second vector search is introduced.

If no evidence passes the configured threshold, the stable no-evidence response is emitted as a delta and citations are empty.

### Business Tools

The selected `FundTools` callback remains restricted by `RouteType`. Spring AI tool execution may be blocking before final text begins, so the stream emits `CALLING_TOOL` status while the model and tool complete that stage. The final model response is still delivered as deltas through `ChatClient.stream()`.

No tool input, SQL detail, or raw tool output is sent as an SSE event.

### Sales Script

The existing compliance system prompt and memory advisor are retained. The final answer uses `ChatClient.stream().content()` and is emitted as deltas.

## Completion, Cancellation, and Errors

- Only one assistant placeholder is created for a request.
- A successful stream must contain non-blank aggregate text before persistence.
- `ChatSessionPersistenceService` runs once after successful generation, then `done` is emitted.
- Client cancellation propagates through Reactor to the model subscription. The application does not write a partial `chat_session` row and does not emit `done`.
- Provider, retrieval, tool, and persistence failures become a generic `error` event suitable for display. Full details remain server-side in structured logs.
- A configurable stream timeout prevents abandoned requests from holding resources indefinitely.
- Lightweight SSE keepalive comments may be emitted during long rewrite, retrieval, or tool phases so reverse proxies do not close an otherwise healthy request.

## Frontend Data Flow

`chat.js` adds a fetch-based streaming client instead of routing streaming responses through Axios, which buffers normal JSON responses.

The streaming client:

1. Sends the existing request DTO to `/api/chat/stream`.
2. Validates the HTTP status and `text/event-stream` content type.
3. Uses `TextDecoder` in streaming mode.
4. Buffers partial network frames until a complete SSE record is available.
5. Dispatches named events to callbacks.
6. Supports `AbortSignal` and distinguishes cancellation from server failure.

The SSE parser is a small standalone utility so split UTF-8 characters, split lines, CRLF separators, multiple `data:` lines, comments, and trailing buffered data can be unit tested without a browser.

The Pinia store inserts a pending assistant message before connecting. `delta` events append content to that object, `metadata` sets the route, `citations` fills references, and `done` marks the message complete. On an error with no content, the pending assistant message is removed; when partial content exists, it is retained with a retryable error state but is not treated as persisted history.

## Interaction Design

The answer occupies its final layout from the first delta so the page does not jump when generation completes. While streaming:

- The previous thinking indicator is replaced by route-aware status until the first text arrives.
- The assistant answer updates in place with a restrained insertion cursor.
- The circular send control becomes a familiar stop icon with an accessible tooltip.
- Automatic scrolling is scheduled at most once per animation frame rather than once per token.
- The composer remains stable in size and prevents a second concurrent send.
- Reduced-motion users receive the same text updates without cursor animation or smooth scrolling.

## Compatibility

- Existing callers of `/api/chat/ask` are unaffected.
- Existing history rows and `ChatResponseDTO` remain valid.
- The frontend uses the stream endpoint by default and can fall back to the synchronous endpoint only when the server does not advertise SSE support.
- Vite continues proxying `/api` to port 8080; no CORS change is required.

## Verification

Backend tests cover:

- SSE event ordering and media type.
- Validation before streaming.
- Multiple deltas aggregating into one persisted answer.
- RAG citations emitted from the same advisor context.
- Sanitized provider errors with no `done` event.
- Cancellation and empty output not persisting a chat session.
- The synchronous `/ask` regression contract.

Frontend tests cover:

- SSE frames split across arbitrary network chunks.
- Unicode characters split across byte chunks.
- `metadata`, `delta`, `citations`, `done`, and `error` dispatch.
- Cancellation state cleanup.

End-to-end verification uses the real DashScope configuration through Vite and checks that at least two visible deltas arrive before `done`, the final answer is persisted in history, citations render when evidence exists, and cancellation returns the composer to an idle state.

## Acceptance Criteria

- The browser receives `text/event-stream` from `POST /api/chat/stream` through port 3000.
- The first answer text renders before the complete model response is available.
- RAG, business-tool, and sales routes retain their existing prompts, tools, memory, and audit semantics.
- RAG citations appear after the answer and are derived from the same streaming advisor execution.
- Stop cancels the request without leaving the UI loading or writing a partial application history row.
- Errors are actionable to the user and do not expose API keys, SQL, prompts, or provider stack traces.
- Backend tests, frontend parser tests, frontend production build, and real local integration all pass on JDK 17.
