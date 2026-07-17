package com.hqc.smrag.service.chat;

import com.hqc.smrag.config.RagProperties;
import com.hqc.smrag.exception.AiServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class KnowledgeQaService {

    public static final String NO_EVIDENCE_RESPONSE =
        "\u5f53\u524d\u77e5\u8bc6\u5e93\u4e2d\u6ca1\u6709\u8db3\u591f\u4f9d\u636e\u56de\u7b54\u8be5\u95ee\u9898\u3002";

    private final KnowledgeChatClient chatClient;
    private final CitationAssembler citationAssembler;
    private final RagProperties ragProperties;

    public KnowledgeQaResult answer(
        String question,
        String sessionId,
        String knowledgeBaseId,
        Long documentId
    ) {
        String normalizedKnowledgeBaseId = normalizeKnowledgeBaseId(knowledgeBaseId);
        KnowledgeChatClient.KnowledgeAnswer knowledgeAnswer;
        try {
            knowledgeAnswer = chatClient.answer(
                question,
                sessionId,
                normalizedKnowledgeBaseId,
                documentId
            );
        } catch (AiServiceException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw new AiServiceException("Knowledge AI request failed", failure);
        }
        if (knowledgeAnswer.documents().isEmpty()) {
            return new KnowledgeQaResult(NO_EVIDENCE_RESPONSE, java.util.List.of());
        }
        return new KnowledgeQaResult(
            knowledgeAnswer.content(),
            citationAssembler.assemble(
                knowledgeAnswer.documents(),
                ragProperties.getMaxContextDocuments()
            )
        );
    }

    private String normalizeKnowledgeBaseId(String knowledgeBaseId) {
        return knowledgeBaseId == null || knowledgeBaseId.isBlank()
            ? "default"
            : knowledgeBaseId.trim();
    }
}
