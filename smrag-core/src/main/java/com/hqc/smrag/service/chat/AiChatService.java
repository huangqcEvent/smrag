package com.hqc.smrag.service.chat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hqc.smrag.config.ChatStreamProperties;
import com.hqc.smrag.dto.ChatRequestDTO;
import com.hqc.smrag.dto.ChatResponseDTO;
import com.hqc.smrag.dto.ChatStreamEvent;
import com.hqc.smrag.dto.CitationDTO;
import com.hqc.smrag.entity.ChatSession;
import com.hqc.smrag.enums.RouteType;
import com.hqc.smrag.exception.AiServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * AI 聊天服务
 * <p>
 * 智能问答的核心服务，负责：
 * <ul>
 *     <li>请求参数校验和规范化</li>
 *     <li>问题重写（结合历史对话上下文）</li>
 *     <li>路由决策（确定问题应该由哪个分支处理）</li>
 *     <li>分支处理（知识库问答、产品查询、销售话术等）</li>
 *     <li>对话记录持久化</li>
 * </ul>
 *
 * @see RouteService 路由决策服务
 * @see KnowledgeQaService 知识库问答服务
 * @see BusinessToolChatService 业务工具聊天服务
 * @see SalesScriptService 销售话术服务
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AiChatService {

    private final ConversationQueryService queryService;
    private final RouteService routeService;
    private final KnowledgeQaService knowledgeQaService;
    private final BusinessToolChatService businessService;
    private final SalesScriptService salesScriptService;
    private final ChatSessionPersistenceService persistenceService;
    private final ObjectMapper objectMapper;
    private final ChatStreamProperties streamProperties;

    /**
     * 处理用户提问
     * <p>
     * 处理流程：
     * <ol>
     *     <li>参数校验：验证 sessionId、question、knowledgeBaseId 等参数</li>
     *     <li>问题重写：结合历史对话，将口语化问题转换为更清晰的查询</li>
     *     <li>路由决策：判断问题类型（知识库问答、产品查询、销售话术等）</li>
     *     <li>分支处理：调用对应的服务处理问题</li>
     *     <li>结果封装：构造响应 DTO</li>
     *     <li>持久化：保存对话记录</li>
     * </ol>
     *
     * @param request 聊天请求
     * @return 聊天响应
     * @throws AiServiceException AI服务调用失败时抛出
     */
    public ChatResponseDTO ask(ChatRequestDTO request) {
        StreamRequest normalized = normalizeStreamRequest(request);
        String sessionId = normalized.sessionId();
        String question = normalized.question();

        String rewrittenQuestion = queryService.rewrite(question, sessionId);
        RouteType routeType = routeService.route(rewrittenQuestion);
        BranchAnswer branchAnswer = answer(
            routeType,
            rewrittenQuestion,
            sessionId,
            normalized.knowledgeBaseId(),
            normalized.documentId()
        );
        if (branchAnswer.answer() == null || branchAnswer.answer().isBlank()) {
            throw new AiServiceException(
                "AI returned an empty answer",
                new IllegalStateException("empty model output")
            );
        }

        persistenceService.save(toSession(
            normalized.source(),
            sessionId,
            question,
            rewrittenQuestion,
            routeType,
            branchAnswer
        ));
        return toResponse(sessionId, routeType, branchAnswer);
    }

    public Flux<ChatStreamEvent> stream(ChatRequestDTO request) {
        Flux<ChatStreamEvent> source = Flux.defer(() -> {
                StreamRequest normalized = normalizeStreamRequest(request);
                Flux<ChatStreamEvent> processing = Mono.fromCallable(() -> {
                        String rewrittenQuestion = queryService.rewrite(
                            normalized.question(),
                            normalized.sessionId()
                        );
                        RouteType routeType = routeService.route(rewrittenQuestion);
                        return new StreamContext(normalized, rewrittenQuestion, routeType);
                    })
                    .subscribeOn(Schedulers.boundedElastic())
                    .flatMapMany(this::streamPrepared);
                return Flux.concat(
                    Flux.just(ChatStreamEvent.status("ANALYZING")),
                    processing
                );
            });
        return enforceTotalTimeout(source, streamProperties.getTimeout())
            .timeout(streamProperties.getTimeout())
            .onErrorResume(failure -> {
                log.warn("Chat stream failed for session {}", sessionIdForLog(request), failure);
                return Flux.just(sanitizedError(failure));
            });
    }

    private Flux<ChatStreamEvent> enforceTotalTimeout(
        Flux<ChatStreamEvent> source,
        Duration timeout
    ) {
        return Flux.defer(() -> {
            AtomicBoolean completed = new AtomicBoolean();
            return source
                .doOnComplete(() -> completed.set(true))
                .takeUntilOther(Mono.delay(timeout))
                .concatWith(Flux.defer(() -> completed.get()
                    ? Flux.empty()
                    : Flux.error(new TimeoutException("Chat stream exceeded its total timeout"))));
        });
    }

    /**
     * 查询会话历史记录
     *
     * @param sessionId 会话ID
     * @return 该会话下的所有对话记录，按时间顺序排列
     */
    public List<ChatSession> history(String sessionId) {
        return persistenceService.history(sessionId);
    }

    /**
     * 根据路由类型调用对应的处理分支
     *
     * @param routeType        路由类型
     * @param question         问题内容
     * @param sessionId        会话ID
     * @param knowledgeBaseId  知识库ID
     * @param documentId       文档ID（可选）
     * @return 分支处理结果
     */
    private BranchAnswer answer(
        RouteType routeType,
        String question,
        String sessionId,
        String knowledgeBaseId,
        Long documentId
    ) {
        return switch (routeType) {
            case KNOWLEDGE_QA -> {
                KnowledgeQaResult result = knowledgeQaService.answer(
                    question,
                    sessionId,
                    knowledgeBaseId,
                    documentId
                );
                yield new BranchAnswer(result.answer(), result.citations());
            }
            case PRODUCT_QUERY, PRODUCT_COMPARE, PRODUCT_RECOMMEND, MANAGER_QUERY ->
                new BranchAnswer(
                    businessService.answer(routeType, question, sessionId),
                    List.of()
                );
            case SALES_SCRIPT -> new BranchAnswer(
                salesScriptService.generate(question, null, sessionId),
                List.of()
            );
        };
    }

    private Flux<ChatStreamEvent> streamPrepared(StreamContext context) {
        StreamAccumulator accumulator = new StreamAccumulator();
        return Flux.concat(
            Flux.just(
                ChatStreamEvent.metadata(
                    context.request().sessionId(),
                    context.routeType().name()
                ),
                ChatStreamEvent.status(phase(context.routeType()))
            ),
            streamBranch(context, accumulator),
            citationsAfterGeneration(context, accumulator),
            persistAndComplete(context, accumulator)
        );
    }

    private Flux<ChatStreamEvent> streamBranch(
        StreamContext context,
        StreamAccumulator accumulator
    ) {
        return switch (context.routeType()) {
            case KNOWLEDGE_QA -> streamKnowledge(context, accumulator);
            case PRODUCT_QUERY, PRODUCT_COMPARE, PRODUCT_RECOMMEND, MANAGER_QUERY ->
                streamText(
                    businessService.stream(
                        context.routeType(),
                        context.rewrittenQuestion(),
                        context.request().sessionId()
                    ),
                    accumulator
                );
            case SALES_SCRIPT -> streamText(
                salesScriptService.stream(
                    context.rewrittenQuestion(),
                    null,
                    context.request().sessionId()
                ),
                accumulator
            );
        };
    }

    private Flux<ChatStreamEvent> streamKnowledge(
        StreamContext context,
        StreamAccumulator accumulator
    ) {
        return knowledgeQaService.stream(
                context.rewrittenQuestion(),
                context.request().sessionId(),
                context.request().knowledgeBaseId(),
                context.request().documentId()
            )
            .map(chunk -> {
                accumulator.append(chunk.answer());
                accumulator.updateCitations(chunk.citations());
                return ChatStreamEvent.delta(chunk.answer());
            })
            .filter(event -> event.content() != null && !event.content().isEmpty());
    }

    private Flux<ChatStreamEvent> citationsAfterGeneration(
        StreamContext context,
        StreamAccumulator accumulator
    ) {
        if (context.routeType() != RouteType.KNOWLEDGE_QA) {
            return Flux.empty();
        }
        return Flux.defer(() -> Flux.just(ChatStreamEvent.citations(
            accumulator.citations(),
            references(accumulator.citations())
        )));
    }

    private Flux<ChatStreamEvent> streamText(
        Flux<String> chunks,
        StreamAccumulator accumulator
    ) {
        return chunks
            .filter(chunk -> !chunk.isEmpty())
            .map(chunk -> {
                accumulator.append(chunk);
                return ChatStreamEvent.delta(chunk);
            });
    }

    private Mono<ChatStreamEvent> persistAndComplete(
        StreamContext context,
        StreamAccumulator accumulator
    ) {
        return Mono.fromCallable(() -> {
                BranchAnswer completed = accumulator.toBranchAnswer();
                if (completed.answer().isBlank()) {
                    throw new AiServiceException(
                        "AI returned an empty answer",
                        new IllegalStateException("empty model output")
                    );
                }
                StreamRequest request = context.request();
                persistenceService.save(toSession(
                    request.source(),
                    request.sessionId(),
                    request.question(),
                    context.rewrittenQuestion(),
                    context.routeType(),
                    completed
                ));
                return ChatStreamEvent.done(
                    request.sessionId(),
                    context.routeType().name()
                );
            })
            .subscribeOn(Schedulers.boundedElastic());
    }

    private StreamRequest normalizeStreamRequest(ChatRequestDTO request) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        return new StreamRequest(
            request,
            requireText(request.getSessionId(), "sessionId"),
            requireText(request.getQuestion(), "question"),
            normalizeKnowledgeBaseId(request.getKnowledgeBaseId()),
            requirePositiveDocumentId(request.getDocumentId())
        );
    }

    private String phase(RouteType routeType) {
        return switch (routeType) {
            case KNOWLEDGE_QA -> "RETRIEVING";
            case PRODUCT_QUERY, PRODUCT_COMPARE, PRODUCT_RECOMMEND, MANAGER_QUERY ->
                "CALLING_TOOL";
            case SALES_SCRIPT -> "GENERATING";
        };
    }

    private ChatStreamEvent sanitizedError(Throwable failure) {
        if (failure instanceof TimeoutException) {
            return ChatStreamEvent.error(
                504,
                "AI \u54cd\u5e94\u8d85\u65f6\uff0c\u8bf7\u7a0d\u540e\u91cd\u8bd5"
            );
        }
        if (failure instanceof IllegalArgumentException) {
            return ChatStreamEvent.error(400, "\u8bf7\u6c42\u53c2\u6570\u4e0d\u6b63\u786e");
        }
        return ChatStreamEvent.error(
            502,
            "AI \u670d\u52a1\u6682\u65f6\u4e0d\u53ef\u7528\uff0c\u8bf7\u7a0d\u540e\u91cd\u8bd5"
        );
    }

    private String sessionIdForLog(ChatRequestDTO request) {
        return request == null ? "<none>" : String.valueOf(request.getSessionId());
    }

    private List<String> references(List<CitationDTO> citations) {
        return citations.stream()
            .map(CitationDTO::documentName)
            .filter(name -> name != null && !name.isBlank())
            .distinct()
            .toList();
    }

    /**
     * 构造聊天会话实体
     */
    private ChatSession toSession(
        ChatRequestDTO request,
        String sessionId,
        String question,
        String rewrittenQuestion,
        RouteType routeType,
        BranchAnswer branchAnswer
    ) {
        ChatSession session = new ChatSession();
        session.setSessionId(sessionId);
        session.setUserId(request.getUserId());
        session.setQuestion(question);
        session.setRewriteQuestion(rewrittenQuestion);
        session.setAnswer(branchAnswer.answer());
        session.setRouteType(routeType.name());
        session.setReferenceDocs(citationsJson(branchAnswer.citations()));
        session.setCreateTime(LocalDateTime.now());
        return session;
    }

    /**
     * 构造聊天响应 DTO
     */
    private ChatResponseDTO toResponse(
        String sessionId,
        RouteType routeType,
        BranchAnswer branchAnswer
    ) {
        ChatResponseDTO response = new ChatResponseDTO();
        response.setSessionId(sessionId);
        response.setAnswer(branchAnswer.answer());
        response.setRouteType(routeType.name());
        response.setCitations(branchAnswer.citations());
        response.setReferences(branchAnswer.citations().stream()
            .map(CitationDTO::documentName)
            .filter(name -> name != null && !name.isBlank())
            .distinct()
            .toList());
        return response;
    }

    /**
     * 校验文本参数非空
     */
    private String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }

    /**
     * 规范化知识库ID
     */
    private String normalizeKnowledgeBaseId(String value) {
        return value == null || value.isBlank() ? "default" : value.trim();
    }

    /**
     * 校验文档ID必须为正数
     */
    private Long requirePositiveDocumentId(Long documentId) {
        if (documentId != null && documentId <= 0) {
            throw new IllegalArgumentException("documentId must be positive");
        }
        return documentId;
    }

    /**
     * 将引用列表序列化为 JSON 字符串
     */
    private String citationsJson(List<CitationDTO> citations) {
        try {
            return objectMapper.writeValueAsString(citations);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize chat citations", exception);
        }
    }

    /**
     * 分支答案内部记录类
     * <p>
     * 封装答案内容和引用来源。
     */
    private record BranchAnswer(String answer, List<CitationDTO> citations) {
        private BranchAnswer {
            citations = citations == null ? List.of() : List.copyOf(citations);
        }
    }

    private record StreamRequest(
        ChatRequestDTO source,
        String sessionId,
        String question,
        String knowledgeBaseId,
        Long documentId
    ) {
    }

    private record StreamContext(
        StreamRequest request,
        String rewrittenQuestion,
        RouteType routeType
    ) {
    }

    private static final class StreamAccumulator {

        private final StringBuilder answer = new StringBuilder();
        private List<CitationDTO> citations = List.of();

        void append(String content) {
            if (content != null) {
                answer.append(content);
            }
        }

        void updateCitations(List<CitationDTO> nextCitations) {
            if (nextCitations != null && !nextCitations.isEmpty()) {
                citations = List.copyOf(nextCitations);
            }
        }

        List<CitationDTO> citations() {
            return citations;
        }

        BranchAnswer toBranchAnswer() {
            return new BranchAnswer(answer.toString(), citations);
        }
    }
}
