package com.hqc.smrag.service.chat;

import com.hqc.smrag.config.RagProperties;
import com.hqc.smrag.dto.CitationDTO;
import com.hqc.smrag.entity.KnowledgeBase;
import com.hqc.smrag.entity.KnowledgeDocument;
import com.hqc.smrag.exception.AiServiceException;
import com.hqc.smrag.exception.DomainBadRequestException;
import com.hqc.smrag.exception.DomainNotFoundException;
import com.hqc.smrag.mapper.KnowledgeDocumentMapper;
import com.hqc.smrag.service.knowledge.KnowledgeBaseService;
import com.hqc.smrag.service.knowledge.PermissionDecisionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.LinkedHashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
public class KnowledgeQaService {

    public static final String NO_EVIDENCE_RESPONSE =
        "当前知识库中没有足够证据回答该问题。";

    private final KnowledgeChatClient chatClient;
    private final CitationAssembler citationAssembler;
    private final RagProperties ragProperties;
    private final KnowledgeBaseService knowledgeBaseService;
    private final PermissionDecisionService permissionDecisionService;
    private final KnowledgeDocumentMapper documentMapper;

    public KnowledgeQaResult answer(
        String question,
        String sessionId,
        List<String> knowledgeBaseCodes,
        Long documentId,
        Long userId
    ) {
        KnowledgeRetrievalScope scope =
            prepareScope(knowledgeBaseCodes, documentId, userId);
        try {
            KnowledgeChatClient.KnowledgeAnswer result =
                chatClient.answer(question, sessionId, scope);
            if (result.documents().isEmpty()) {
                return new KnowledgeQaResult(NO_EVIDENCE_RESPONSE, List.of());
            }
            return new KnowledgeQaResult(
                result.content(),
                citationAssembler.assemble(
                    result.documents(),
                    ragProperties.getMaxContextDocuments()
                )
            );
        } catch (AiServiceException failure) {
            throw failure;
        } catch (IllegalArgumentException | AccessDeniedException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw new AiServiceException("Knowledge AI request failed", failure);
        }
    }

    public Flux<KnowledgeQaResult> stream(
        String question,
        String sessionId,
        List<String> knowledgeBaseCodes,
        Long documentId,
        Long userId
    ) {
        return Flux.defer(() -> {
            KnowledgeRetrievalScope scope =
                prepareScope(knowledgeBaseCodes, documentId, userId);
            return mapStream(chatClient.stream(question, sessionId, scope));
        });
    }

    KnowledgeRetrievalScope prepareScope(
        List<String> knowledgeBaseCodes,
        Long documentId,
        Long userId
    ) {
        if (userId == null || userId <= 0) {
            throw new AccessDeniedException("用户未登录");
        }
        LinkedHashSet<String> codes = knowledgeBaseCodes == null
            ? new LinkedHashSet<>()
            : knowledgeBaseCodes.stream()
                .filter(java.util.Objects::nonNull)
                .map(String::trim)
                .filter(code -> !code.isEmpty())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (codes.isEmpty()) {
            throw new IllegalArgumentException("至少选择一个知识库");
        }
        if (codes.size() > 5) {
            throw new DomainBadRequestException(
                "KNOWLEDGE_BASE_LIMIT_EXCEEDED",
                "一次最多选择 5 个知识库",
                java.util.Map.of("limit", 5, "actual", codes.size())
            );
        }
        List<KnowledgeBase> bases = codes.stream()
            .map(knowledgeBaseService::requireActive)
            .toList();
        List<Long> baseIds = bases.stream().map(KnowledgeBase::getId).toList();
        permissionDecisionService.requireKnowledgeBaseAccess(userId, baseIds);

        if (documentId != null) {
            KnowledgeDocument document = documentMapper.selectById(documentId);
            if (document == null) {
                throw new DomainNotFoundException(
                    "DOCUMENT_NOT_FOUND", "文档不存在",
                    java.util.Map.of("documentId", documentId)
                );
            }
            if (!baseIds.contains(document.getKnowledgeBaseId())) {
                throw new IllegalArgumentException("文档不属于已选择的知识库");
            }
            permissionDecisionService.requireDocumentRead(userId, documentId);
        }
        return new KnowledgeRetrievalScope(
            userId,
            baseIds,
            documentId,
            permissionDecisionService.isGlobalAdmin(userId)
        );
    }

    private Flux<KnowledgeQaResult> mapStream(
        Flux<KnowledgeChatClient.KnowledgeAnswer> source
    ) {
        return source
            .switchOnFirst((signal, answers) -> {
                if (!signal.hasValue()) {
                    return answers.map(answer ->
                        new KnowledgeQaResult(answer.content(), List.of()));
                }
                KnowledgeChatClient.KnowledgeAnswer first = signal.get();
                if (first.documents().isEmpty()) {
                    return Flux.just(
                        new KnowledgeQaResult(NO_EVIDENCE_RESPONSE, List.of()));
                }
                List<CitationDTO> citations = citationAssembler.assemble(
                    first.documents(),
                    ragProperties.getMaxContextDocuments()
                );
                return answers.map(answer ->
                    new KnowledgeQaResult(answer.content(), citations));
            })
            .onErrorMap(
                failure -> !(failure instanceof AiServiceException)
                    && !(failure instanceof IllegalArgumentException)
                    && !(failure instanceof AccessDeniedException),
                failure -> new AiServiceException("Knowledge AI request failed", failure)
            );
    }
}
