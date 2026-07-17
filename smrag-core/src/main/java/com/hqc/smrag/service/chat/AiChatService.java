package com.hqc.smrag.service.chat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hqc.smrag.dto.ChatRequestDTO;
import com.hqc.smrag.dto.ChatResponseDTO;
import com.hqc.smrag.dto.CitationDTO;
import com.hqc.smrag.entity.ChatSession;
import com.hqc.smrag.enums.RouteType;
import com.hqc.smrag.exception.AiServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

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
public class AiChatService {

    private final ConversationQueryService queryService;
    private final RouteService routeService;
    private final KnowledgeQaService knowledgeQaService;
    private final BusinessToolChatService businessService;
    private final SalesScriptService salesScriptService;
    private final ChatSessionPersistenceService persistenceService;
    private final ObjectMapper objectMapper;

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
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        String sessionId = requireText(request.getSessionId(), "sessionId");
        String question = requireText(request.getQuestion(), "question");
        String knowledgeBaseId = normalizeKnowledgeBaseId(request.getKnowledgeBaseId());
        Long documentId = requirePositiveDocumentId(request.getDocumentId());

        String rewrittenQuestion = queryService.rewrite(question, sessionId);
        RouteType routeType = routeService.route(rewrittenQuestion);
        BranchAnswer branchAnswer = answer(
            routeType,
            rewrittenQuestion,
            sessionId,
            knowledgeBaseId,
            documentId
        );
        if (branchAnswer.answer() == null || branchAnswer.answer().isBlank()) {
            throw new AiServiceException(
                "AI returned an empty answer",
                new IllegalStateException("empty model output")
            );
        }

        persistenceService.save(toSession(
            request,
            sessionId,
            question,
            rewrittenQuestion,
            routeType,
            branchAnswer
        ));
        return toResponse(sessionId, routeType, branchAnswer);
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
}
