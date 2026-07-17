package com.hqc.smrag.service.chat;

import com.hqc.smrag.config.RagProperties;
import com.hqc.smrag.exception.AiServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * \u77e5\u8bc6\u5e93\u95ee\u7b54\u670d\u52a1
 * <p>
 * \u57fa\u4e8e RAG\uff08\u68c0\u7d22\u589e\u5f3a\u751f\u6210\uff09\u67b6\u6784\u7684\u77e5\u8bc6\u5e93\u95ee\u7b54\u670d\u52a1\u3002
 * \u5904\u7406\u6d41\u7a0b\uff1a
 * <ol>
 *     <li>\u4ece\u5411\u91cf\u6570\u636e\u5e93\u68c0\u7d22\u76f8\u5173\u6587\u6863\u7247\u6bb5</li>
 *     <li>\u5c06\u68c0\u7d22\u7ed3\u679c\u4f5c\u4e3a\u4e0a\u4e0b\u6587\u4f20\u9012\u7ed9\u5927\u8bed\u8a00\u6a21\u578b</li>
 *     <li>\u751f\u6210\u57fa\u4e8e\u4e8b\u5b9e\u7684\u7b54\u6848</li>
 *     <li>\u7ec4\u88c5\u5f15\u7528\u6765\u6e90\u4fe1\u606f</li>
 * </ol>
 *
 * @see KnowledgeChatClient \u77e5\u8bc6\u5e93\u804a\u5929\u5ba2\u6237\u7aef
 * @see CitationAssembler \u5f15\u7528\u7ec4\u88c5\u5668
 */
@Service
@RequiredArgsConstructor
public class KnowledgeQaService {

    /**
     * \u5f53\u77e5\u8bc6\u5e93\u4e2d\u6ca1\u6709\u8db3\u591f\u8bc1\u636e\u65f6\u7684\u9ed8\u8ba4\u54cd\u5e94
     */
    public static final String NO_EVIDENCE_RESPONSE =
        "\u5f53\u524d\u77e5\u8bc6\u5e93\u4e2d\u6ca1\u6709\u8db3\u591f\u4f9d\u636e\u56de\u7b54\u8be5\u95ee\u9898\u3002";

    private final KnowledgeChatClient chatClient;
    private final CitationAssembler citationAssembler;
    private final RagProperties ragProperties;

    /**
     * \u56de\u7b54\u77e5\u8bc6\u5e93\u95ee\u9898
     * <p>
     * \u6839\u636e\u95ee\u9898\u4ece\u5411\u91cf\u6570\u636e\u5e93\u68c0\u7d22\u76f8\u5173\u6587\u6863\uff0c\u7ed3\u5408\u4e0a\u4e0b\u6587\u751f\u6210\u7b54\u6848\u3002
     * \u5982\u679c\u6ca1\u6709\u68c0\u7d22\u5230\u76f8\u5173\u6587\u6863\uff0c\u8fd4\u56de\u9ed8\u8ba4\u7684\u65e0\u8bc1\u636e\u54cd\u5e94\u3002
     *
     * @param question        \u7528\u6237\u95ee\u9898
     * @param sessionId       \u4f1a\u8bddID\uff0c\u7528\u4e8e\u591a\u8f6e\u5bf9\u8bdd\u8bb0\u5fc6
     * @param knowledgeBaseId \u77e5\u8bc6\u5e93ID\uff0c\u7528\u4e8e\u6570\u636e\u9694\u79bb
     * @param documentId      \u6587\u6863ID\uff08\u53ef\u9009\uff09\uff0c\u9650\u5b9a\u68c0\u7d22\u8303\u56f4
     * @return \u95ee\u7b54\u7ed3\u679c\uff0c\u5305\u542b\u7b54\u6848\u548c\u5f15\u7528\u6765\u6e90
     * @throws AiServiceException AI \u670d\u52a1\u8c03\u7528\u5931\u8d25\u65f6\u629b\u51fa
     */
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

    /**
     * \u89c4\u8303\u5316\u77e5\u8bc6\u5e93ID
     * <p>
     * \u5982\u679c\u77e5\u8bc6\u5e93ID\u4e3a\u7a7a\u6216\u7a7a\u767d\uff0c\u8fd4\u56de\u9ed8\u8ba4\u503c "default"\u3002
     */
    private String normalizeKnowledgeBaseId(String knowledgeBaseId) {
        return knowledgeBaseId == null || knowledgeBaseId.isBlank()
            ? "default"
            : knowledgeBaseId.trim();
    }
}
