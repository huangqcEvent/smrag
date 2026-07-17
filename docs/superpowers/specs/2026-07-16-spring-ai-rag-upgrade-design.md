# Spring AI RAG Upgrade Design

## Status

The architecture, data flow, reliability model, and test scope in this document were approved section by section in the project conversation. This written specification is ready for final user review before implementation planning.

## Objective

Upgrade the JDK 17 application from the Spring AI `1.0.0-M2` preview APIs to a supported Spring AI Alibaba stack, replace manual AI orchestration with Spring AI advisors, memory, structured output, tools, and ETL components, and preserve the existing synchronous API while adding streaming chat and reliable citations.

## Scope

This iteration includes:

- Dependency alignment for JDK 17.
- Structured query routing.
- Spring AI chat memory.
- Modular RAG with filtered retrieval and citations.
- Spring AI tool calling for fund and manager operations.
- Spring AI document readers and token-based splitting.
- Idempotent Kafka ingestion, retry, dead-letter handling, and compensating cleanup.
- PGVector schema migration and deterministic vector identifiers.
- Synchronous and SSE chat APIs.
- Spring AI observability and RAG evaluation tests.

This iteration does not include authentication, RBAC, tenant isolation, a knowledge-base management UI, hybrid keyword/vector retrieval, an external reranking service, or Spring AI Alibaba Graph/Agent Framework. The design adds `knowledgeBaseId` to backend contracts and metadata so these capabilities can be introduced later without reindexing every document.

## Dependency Baseline

The project remains on JDK 17 and uses the following compatible release line:

- Spring Boot `3.5.8`.
- Spring AI `1.1.2` through `spring-ai-bom`.
- Spring AI Alibaba `1.1.2.0` through `spring-ai-alibaba-bom`.
- Spring AI Alibaba Extensions `1.1.2.1` through `spring-ai-alibaba-extensions-bom` for DashScope integration.

The application replaces `spring-ai-alibaba-starter` with `spring-ai-alibaba-starter-dashscope` and replaces the old PGVector library dependency with `spring-ai-starter-vector-store-pgvector`. It also adds the Spring AI RAG, Tika reader, PDF reader, JDBC chat-memory repository, WebFlux, Actuator, and test modules required by the design.

Milestone and snapshot repositories are removed. Release dependencies resolve from Maven Central, and all Spring AI family versions are controlled by BOMs rather than per-dependency versions.

## Architecture Decision

The selected approach is controlled dual-path orchestration. A structured router selects a deterministic knowledge, business-tool, or sales path. The application does not use one unrestricted agent for all requests and does not introduce Spring AI Alibaba Graph in this iteration.

```text
ChatController
      |
      v
AiChatService
      |
      +-- ConversationQueryService
      |     ChatMemory + RewriteQueryTransformer
      |
      +-- RouteDecisionService
      |     ChatClient.entity(RouteDecision.class)
      |
      +-- KnowledgeAnswerService
      |     MessageChatMemoryAdvisor
      |     RetrievalAugmentationAdvisor
      |     VectorStoreDocumentRetriever
      |
      +-- BusinessAnswerService
      |     MessageChatMemoryAdvisor
      |     ChatClient.tools(FundTools)
      |
      +-- SalesAnswerService
            MessageChatMemoryAdvisor
            compliance prompt + selected tools
```

`ChatController` validates HTTP input and delegates to `AiChatService`. `AiChatService` owns orchestration and persistence. Each AI-facing component has one responsibility and can be tested with a controlled `ChatModel`.

## Chat Data Flow

1. Validate that `sessionId` and `question` are present and within configured length limits. If `knowledgeBaseId` is absent, use `default`.
2. Read at most 12 messages from Spring AI `ChatMemory` for the session.
3. Build a Spring AI `Query` from the current question and memory snapshot. `RewriteQueryTransformer` produces a standalone question without mutating memory.
4. `RouteDecisionService` sends the standalone question to a routing `ChatClient` and converts the result directly to `RouteDecision` with `.entity(RouteDecision.class)`.
5. Knowledge requests use `KnowledgeAnswerService`; product, comparison, recommendation, and manager requests use `BusinessAnswerService`; sales-script requests use `SalesAnswerService`.
6. The selected final-answer client receives the standalone question and the session conversation ID. `MessageChatMemoryAdvisor` stores the standalone question and final answer as semantic conversation memory.
7. The existing `chat_session` table stores the original question, standalone question, route, final answer, and citations for user-visible history and audit purposes.
8. `ChatResponseDTO` is returned through the existing `Result` envelope.

The semantic memory intentionally stores the standalone question, while the audit table preserves the user's original wording. Router and rewrite model calls are not stored as conversation turns.

## Structured Routing

`RouteDecision` contains:

- `routeType`: `KNOWLEDGE_QA`, `PRODUCT_QUERY`, `PRODUCT_COMPARE`, `PRODUCT_RECOMMEND`, `MANAGER_QUERY`, or `SALES_SCRIPT`.
- `reason`: a short operational reason used only in debug logs and tests.

The route client uses a temperature of zero. A conversion or validation failure falls back to `KNOWLEDGE_QA`, marks the decision as a fallback in metrics, and never exposes raw model output to the client.

All four business route types use the same business `ChatClient`; route type remains specific for audit and frontend compatibility, while the model chooses the exact tool within the restricted business tool set.

## Retrieval Design

`KnowledgeAnswerService` uses `RetrievalAugmentationAdvisor` with `VectorStoreDocumentRetriever`.

Default configuration:

- Candidate count: `topK=8`.
- Similarity threshold: `0.65`.
- Maximum context documents after deduplication: `5`.
- Empty context: not allowed.
- Distance: cosine.
- PGVector index: HNSW.

These values live under `smrag.ai.rag` configuration properties and can be tuned without code changes.

Every request applies a portable metadata filter containing `knowledgeBaseId` and `documentStatus == 'COMPLETED'`. An optional `documentId` filter may further restrict retrieval. Retrieved documents are deduplicated by vector ID before prompt augmentation.

If no candidate reaches the threshold, the service returns a stable "no reliable knowledge found" response and does not ask the model to answer from general knowledge.

The answer and citation list are derived from the same advisor execution. The application must not run a second vector search to produce references.

## Citations

Each indexed Spring AI `Document` contains these metadata fields:

- `knowledgeBaseId`
- `documentId`
- `documentName`
- `documentType`
- `documentVersion`
- `documentStatus`
- `chunkIndex`
- `pageNumber` when the reader provides one
- `checksum`

The API adds a `citations` collection containing document ID, document name, chunk index, optional page number, and similarity score. The existing `references` string list remains during this iteration and is derived from distinct citation document names.

The frontend displays citations after the generated answer and does not infer citations from answer text.

## Tool Calling

The existing `Function<String, String>` components are replaced by a cohesive `FundTools` component with declarative Spring AI methods:

- `@Tool queryProduct(String productName)`
- `@Tool queryManager(String managerName)`
- `@Tool compareProducts(List<String> productNames)`
- `@Tool recommendProducts(String riskLevel, BigDecimal maxAmount)`

Descriptions state when each tool should be used. Parameters use `@ToolParam` descriptions and application validation. Tools return typed DTOs or lists rather than preformatted prose. The business `ChatClient` serializes tool results and composes the final user-facing answer.

Tools are read-only, do not return `Optional`, reactive types, or Java functional types, and do not expose mapper or SQL exceptions. Sales generation receives only the tools needed to obtain product facts.

## Chat Memory

Spring AI `MessageWindowChatMemory` keeps at most 12 messages per conversation and uses `JdbcChatMemoryRepository` with PostgreSQL persistence. `MessageChatMemoryAdvisor` is registered only on final-answer clients, not on rewrite and routing clients.

Schema initialization is disabled in application configuration. The official PostgreSQL chat-memory schema is copied into a versioned database migration so production startup never mutates schema implicitly.

The existing `chat_session` table remains the authoritative user-facing history and audit store. Spring AI chat memory is an LLM context store and may use its own retention policy.

## Document Ingestion

1. Upload validation checks filename, extension, configured file-size limit, and non-empty content.
2. The service computes a SHA-256 checksum, assigns `knowledgeBaseId=default` when omitted, increments document version, persists `PENDING`, and publishes a versioned Kafka task.
3. The consumer verifies document ID, version, checksum, and current status. A task for an already completed version is acknowledged without reprocessing.
4. PDFs use `PagePdfDocumentReader` so page metadata is retained. DOC, DOCX, and TXT files use `TikaDocumentReader`.
5. `TokenTextSplitter` uses an 800-token target, a 350-character minimum chunk size, a 20-character minimum embed length, a 5,000-chunk safety limit, separator retention, and Chinese sentence punctuation.
6. Reader metadata is merged with the application metadata defined in the citation section.
7. Each chunk receives a deterministic UUID generated with `UUID.nameUUIDFromBytes` over `knowledgeBaseId/documentId/version/chunkIndex`. The same UUID is stored in `knowledge_chunk.vector_id` and used as the Spring AI `Document` ID.
8. Chunk rows and vectors are written in bounded batches. The document becomes `COMPLETED` only after both stores succeed.
9. On failure, the consumer deletes vector IDs and chunk rows written for the failed document version, records a sanitized failure reason, sets `FAILED`, and rethrows so Kafka retry policy applies.

## Delete Flow

Deletion is idempotent:

1. Load the document and all chunk vector IDs.
2. Delete vectors with `VectorStore.delete(ids)`.
3. Delete chunk rows for the document version.
4. Delete the stored file.
5. Delete the document row.

If vector deletion fails, relational rows and the source file remain so the operation can be retried. Repeating deletion for a missing document succeeds without side effects.

## Kafka Reliability

The listener does not swallow processing exceptions. Spring Kafka uses three attempts with exponential backoff, followed by a `document-parse.DLT` dead-letter topic.

The final recoverer sets the matching document version to `FAILED`, stores a sanitized failure reason, and records the dead-letter event. Idempotency is based on document ID, version, and checksum, so duplicate delivery cannot create duplicate chunks or vectors.

## Database Migration

Flyway becomes the schema owner. Existing `sql/init.sql` remains a local reference but application evolution uses ordered files under `smrag-core/src/main/resources/db/migration`.

The current `vector_store` table is incompatible with the Spring AI release schema because it uses a `BIGSERIAL` primary key instead of UUID. The migration therefore creates `rag_vector_store` with the Spring AI UUID/content/metadata/embedding layout and an HNSW cosine index. Application properties set:

- `table-name=rag_vector_store`
- `dimensions=1536`
- `index-type=HNSW`
- `distance-type=COSINE_DISTANCE`
- `initialize-schema=false`
- `schema-validation=true`

Document storage gains `knowledge_base_id`, `checksum`, `version`, and `failure_reason`. Chunk storage gains `vector_id`, `page_number`, and `document_version`, with a unique constraint on `vector_id`.

The old `vector_store` table is not dropped in this iteration. A property-gated reindex runner, enabled with `smrag.ai.reindex-on-startup=true`, reads existing `knowledge_chunk` rows, constructs the new metadata, and writes to `rag_vector_store`. The property defaults to false and is disabled again after a successful migration run.

## API and Streaming

`POST /api/chat/ask` remains synchronous and backward compatible. `POST /api/chat/ask/stream` returns `text/event-stream` with these event types:

- `meta`: session ID and route type.
- `delta`: generated answer text fragments.
- `citations`: final structured citations.
- `done`: completion marker.
- `error`: stable error code and client-safe message.

The route is selected before streaming begins. The final answer is accumulated server-side so chat memory and `chat_session` are written only after successful stream completion. Client cancellation cancels the model subscription and does not persist a partial assistant answer.

The Vue chat view consumes SSE, renders incremental text, prevents duplicate submission while a request is active, and renders citations after completion. The synchronous API remains available for compatibility and tests.

## Error Handling

AI provider failures, structured-output failures, tool validation failures, retrieval failures, ingestion failures, and persistence failures map to distinct application exceptions. `GlobalExceptionHandler` converts them to stable error codes and HTTP statuses.

Services do not catch broad exceptions and return plausible answer strings. Raw provider messages, SQL text, prompt content, tool arguments, and stack traces are never returned to the browser.

The only intentional functional fallback is an invalid route result falling back to `KNOWLEDGE_QA`. Empty retrieval does not fall back to general model knowledge.

## Observability

Spring Boot Actuator, Micrometer, and Spring AI observations provide metrics and traces for:

- Chat and embedding request duration and failures.
- Token usage when supplied by DashScope.
- Advisor execution duration.
- Retrieval candidate count and score distribution.
- Tool invocation count, duration, and failures.
- Kafka retry and dead-letter counts.
- Ingestion duration and chunk count.

Logs and traces carry request trace ID, session ID, document ID, and knowledge-base ID. Full prompts, model answers, tool arguments, and retrieved document content are disabled in production observability settings.

## Testing Strategy

Unit tests cover:

- Structured route conversion and fallback.
- Query rewriting with supplied history.
- Tool parameter validation and typed results.
- Split-document metadata and deterministic UUID generation.
- Citation assembly from one retrieval result.
- Empty-context refusal.
- Exception-to-API mapping.

Integration tests use Testcontainers for PostgreSQL/PGVector and Kafka. They verify schema compatibility, metadata filtering, threshold behavior, vector deletion, duplicate-message idempotency, retry, dead-letter recovery, and JDBC chat-memory persistence.

AI orchestration tests use a deterministic test `ChatModel` so normal builds do not require `DASHSCOPE_API_KEY`. Real-model evaluation is isolated under the `ai-eval` Maven profile and uses Spring AI `RelevancyEvaluator` with a checked-in private-fund question set. Evaluation reports retrieval relevance and answer grounding without blocking ordinary unit tests when credentials are unavailable.

Frontend verification covers synchronous fallback, stream event handling, cancellation, retryable errors, disabled-submit state, and citation rendering.

## Acceptance Criteria

- The complete Maven build compiles with JDK 17.
- No milestone or snapshot repository is required.
- One knowledge question performs one vector retrieval and uses that result for both answer context and citations.
- Retrieval always filters by knowledge base and completed document status.
- Empty or low-score retrieval cannot produce an ungrounded answer.
- Product, comparison, recommendation, and manager tools can be selected and invoked through Spring AI tool calling.
- Conversation context survives an application restart through JDBC chat memory.
- Reprocessing the same Kafka task does not duplicate chunks or vectors.
- Deleting a document removes its vectors, chunk rows, and file, and the content is no longer retrievable.
- Both synchronous and SSE chat APIs complete successfully in integration tests.
- The frontend displays streamed output and structured citations without breaking existing history loading.

## Rollout Order

1. Align dependencies and restore a clean JDK 17 build.
2. Add Flyway migrations and switch PGVector to `rag_vector_store`.
3. Replace ingestion with Spring AI readers, splitting, deterministic IDs, and Kafka reliability.
4. Add chat memory, query rewriting, structured routing, and tool calling.
5. Add modular RAG, single-pass citations, and retrieval safeguards.
6. Add SSE and frontend streaming/citation support.
7. Add observability, Testcontainers coverage, and the optional AI evaluation profile.
8. Run the property-gated reindex once in the target environment, verify retrieval, then disable the property.

This order keeps each stage buildable and makes data migration observable before chat traffic depends on the new vector table.
