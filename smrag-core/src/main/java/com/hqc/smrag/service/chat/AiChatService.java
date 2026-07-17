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

    public List<ChatSession> history(String sessionId) {
        return persistenceService.history(sessionId);
    }

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

    private String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }

    private String normalizeKnowledgeBaseId(String value) {
        return value == null || value.isBlank() ? "default" : value.trim();
    }

    private Long requirePositiveDocumentId(Long documentId) {
        if (documentId != null && documentId <= 0) {
            throw new IllegalArgumentException("documentId must be positive");
        }
        return documentId;
    }

    private String citationsJson(List<CitationDTO> citations) {
        try {
            return objectMapper.writeValueAsString(citations);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize chat citations", exception);
        }
    }

    private record BranchAnswer(String answer, List<CitationDTO> citations) {
        private BranchAnswer {
            citations = citations == null ? List.of() : List.copyOf(citations);
        }
    }
}
