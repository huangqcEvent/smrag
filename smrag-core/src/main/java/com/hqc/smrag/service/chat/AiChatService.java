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
import com.hqc.smrag.service.DocPermissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
@RequiredArgsConstructor(onConstructor_ = @Autowired)
@Slf4j
public class AiChatService {

    private final ConversationQueryService queryService;
    private final RouteService routeService;
    private final KnowledgeQaService knowledgeQaService;
    private final BusinessToolChatService businessService;
    private final SalesScriptService salesScriptService;
    private final ChatSessionPersistenceService persistenceService;
    private final DocPermissionService docPermissionService;
    private final ObjectMapper objectMapper;
    private final ChatStreamProperties streamProperties;

    /** Compatibility constructor for callers that do not perform authenticated retrieval. */
    public AiChatService(ConversationQueryService queryService, RouteService routeService,
                         KnowledgeQaService knowledgeService, BusinessToolChatService businessService,
                         SalesScriptService salesScriptService,
                         ChatSessionPersistenceService persistenceService,
                         ObjectMapper objectMapper, ChatStreamProperties streamProperties) {
        this(queryService, routeService, knowledgeService, businessService, salesScriptService,
            persistenceService, null, objectMapper, streamProperties);
    }

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
        Long userId = normalized.userId();

        // 获取用户可访问的文档ID列表（用于权限过滤）
        List<Long> accessibleDocIds = userId != null && docPermissionService != null
            ? docPermissionService.getAccessibleDocIds(userId)
            : null;

        String rewrittenQuestion = queryService.rewrite(question, sessionId);
        RouteType routeType = routeService.route(rewrittenQuestion);
        BranchAnswer branchAnswer = answer(
            routeType,
            rewrittenQuestion,
            sessionId,
            normalized.knowledgeBaseId(),
            normalized.documentId(),
            accessibleDocIds
        );
        branchAnswer = revalidateCitations(branchAnswer, userId);
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

    /**
     * 流式问答处理入口
     * <p>
     * 处理流程：
     * <ol>
     *     <li>规范化请求参数（sessionId、question、knowledgeBaseId）</li>
     *     <li>获取用户可访问的文档ID列表（用于权限过滤）</li>
     *     <li>发送 ANALYZING 状态事件</li>
     *     <li>异步执行：问题重写 → 路由决策 → 流式生成</li>
     *     <li>超时控制和错误处理</li>
     * </ol>
     *
     * @param request 聊天请求
     * @return SSE 事件流
     */
    public Flux<ChatStreamEvent> stream(ChatRequestDTO request) {
        Flux<ChatStreamEvent> source = Flux.defer(() -> {
                StreamRequest normalized = normalizeStreamRequest(request);
                Long userId = normalized.userId();

                // 获取用户可访问的文档ID列表（用于权限过滤）
                List<Long> accessibleDocIds = userId != null && docPermissionService != null
                    ? docPermissionService.getAccessibleDocIds(userId)
                    : null;

                Flux<ChatStreamEvent> processing = Mono.fromCallable(() -> {
                        String rewrittenQuestion = queryService.rewrite(
                            normalized.question(),
                            normalized.sessionId()
                        );
                        RouteType routeType = routeService.route(rewrittenQuestion);
                        return new StreamContext(normalized, rewrittenQuestion, routeType, accessibleDocIds);
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
     * @param userId    当前用户ID（用于权限验证）
     * @return 该会话下的所有对话记录，按时间顺序排列
     */
    public List<ChatSession> history(String sessionId, Long userId) {
        return persistenceService.history(sessionId, userId);
    }

    /**
     * 查询用户的所有会话
     *
     * @param userId 用户ID
     * @return 会话列表
     */
    public List<ChatSession> getUserSessions(Long userId) {
        return persistenceService.getUserSessions(userId);
    }

    /**
     * 根据路由类型调用对应的处理分支
     *
     * @param routeType        路由类型
     * @param question         问题内容
     * @param sessionId        会话ID
     * @param knowledgeBaseId  知识库ID
     * @param documentId       文档ID（可选）
     * @param accessibleDocIds 用户可访问的文档ID列表
     * @return 分支处理结果
     */
    private BranchAnswer answer(
        RouteType routeType,
        String question,
        String sessionId,
        String knowledgeBaseId,
        Long documentId,
        List<Long> accessibleDocIds
    ) {
        return switch (routeType) {
            case KNOWLEDGE_QA -> {
                KnowledgeQaResult result = accessibleDocIds == null
                    ? knowledgeQaService.answer(question, sessionId, knowledgeBaseId, documentId)
                    : knowledgeQaService.answer(question, sessionId, knowledgeBaseId, documentId,
                        accessibleDocIds);
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

    /**
     * 流式处理准备
     * <p>
     * 按顺序执行以下步骤：
     * <ol>
     *     <li>发送 metadata 事件（sessionId、routeType）</li>
     *     <li>发送 status 事件（RETRIEVING/CALLING_TOOL/GENERATING）</li>
     *     <li>调用对应分支的流式生成方法</li>
     *     <li>发送 citations 事件（仅知识库问答）</li>
     *     <li>持久化对话记录并发送 done 事件</li>
     * </ol>
     *
     * @param context    流式上下文（包含请求、重写问题、路由类型、权限列表）
     * @param accumulator 流式累加器（累积答案和引用）
     * @return SSE 事件流
     */
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

    /**
     * 根据路由类型调用对应的流式处理分支
     * <p>
     * 分支类型：
     * <ul>
     *     <li>KNOWLEDGE_QA：知识库问答，调用 KnowledgeQaService.stream()</li>
     *     <li>PRODUCT_QUERY/COMPARE/RECOMMEND/MANAGER_QUERY：业务查询，调用 BusinessToolChatService.stream()</li>
     *     <li>SALES_SCRIPT：销售话术，调用 SalesScriptService.stream()</li>
     * </ul>
     *
     * @param context    流式上下文
     * @param accumulator 流式累加器
     * @return SSE 事件流（delta 事件）
     */
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

    /**
     * 流式知识库问答处理
     * <p>
     * 调用 KnowledgeQaService.stream() 获取流式答案，将每个答案片段转换为 delta 事件。
     * 同时累积答案内容和引用来源，供后续发送 citations 事件使用。
     *
     * @param context    流式上下文
     * @param accumulator 流式累加器
     * @return SSE 事件流（delta 事件）
     */
    private Flux<ChatStreamEvent> streamKnowledge(
        StreamContext context,
        StreamAccumulator accumulator
    ) {
        Flux<KnowledgeQaResult> source = context.accessibleDocIds() == null
            ? knowledgeQaService.stream(context.rewrittenQuestion(), context.request().sessionId(),
                context.request().knowledgeBaseId(), context.request().documentId())
            : knowledgeQaService.stream(context.rewrittenQuestion(), context.request().sessionId(),
                context.request().knowledgeBaseId(), context.request().documentId(),
                context.accessibleDocIds());
        return source
            .map(chunk -> {
                accumulator.append(chunk.answer());
                accumulator.updateCitations(chunk.citations());
                return ChatStreamEvent.delta(chunk.answer());
            })
            .filter(event -> event.content() != null && !event.content().isEmpty());
    }

    /**
     * 发送引用来源事件
     * <p>
     * 仅在知识库问答路由下发送，包含引用文档列表和文档名称列表。
     * 引用来源从累加器中获取，在 streamKnowledge 阶段已累积完成。
     *
     * @param context    流式上下文
     * @param accumulator 流式累加器
     * @return SSE 事件流（citations 事件）
     */
    private Flux<ChatStreamEvent> citationsAfterGeneration(
        StreamContext context,
        StreamAccumulator accumulator
    ) {
        if (context.routeType() != RouteType.KNOWLEDGE_QA) {
            return Flux.empty();
        }
        return Flux.defer(() -> {
            BranchAnswer revalidated = revalidateCitations(
                accumulator.toBranchAnswer(),
                context.request().userId()
            );
            accumulator.replaceCitations(revalidated.citations());
            return Flux.just(ChatStreamEvent.citations(
                revalidated.citations(),
                references(revalidated.citations())
            ));
        });
    }

    /**
     * 流式文本处理（用于业务查询和销售话术）
     * <p>
     * 将文本片段转换为 delta 事件，同时累积到累加器中。
     *
     * @param chunks     文本片段流
     * @param accumulator 流式累加器
     * @return SSE 事件流（delta 事件）
     */
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

    /**
     * 持久化对话记录并发送完成事件
     * <p>
     * 在流式生成完成后执行：
     * <ol>
     *     <li>检查答案是否为空（为空则抛出异常）</li>
     *     <li>构造 ChatSession 实体并持久化到数据库</li>
     *     <li>发送 done 事件</li>
     * </ol>
     *
     * @param context    流式上下文
     * @param accumulator 流式累加器
     * @return SSE 事件（done 事件）
     */
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
            requirePositiveDocumentId(request.getDocumentId()),
            request.getUserId()
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

    private BranchAnswer revalidateCitations(BranchAnswer answer, Long userId) {
        if (answer.citations().isEmpty() || docPermissionService == null) {
            return answer;
        }
        if (userId == null) {
            return new BranchAnswer(answer.answer(), List.of());
        }
        Set<Long> accessibleDocIds = new HashSet<>(
            docPermissionService.getAccessibleDocIds(userId)
        );
        List<CitationDTO> citations = answer.citations().stream()
            .filter(citation -> citation.documentId() != null
                && accessibleDocIds.contains(citation.documentId()))
            .toList();
        return new BranchAnswer(answer.answer(), citations);
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
        Long documentId,
        Long userId
    ) {
    }

    private record StreamContext(
        StreamRequest request,
        String rewrittenQuestion,
        RouteType routeType,
        List<Long> accessibleDocIds
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

        void replaceCitations(List<CitationDTO> nextCitations) {
            citations = nextCitations == null ? List.of() : List.copyOf(nextCitations);
        }

        List<CitationDTO> citations() {
            return citations;
        }

        BranchAnswer toBranchAnswer() {
            return new BranchAnswer(answer.toString(), citations);
        }
    }
}
