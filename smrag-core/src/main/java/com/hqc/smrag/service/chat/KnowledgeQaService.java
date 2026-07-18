package com.hqc.smrag.service.chat;

import com.hqc.smrag.config.RagProperties;
import com.hqc.smrag.dto.CitationDTO;
import com.hqc.smrag.exception.AiServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 知识库问答服务
 * <p>
 * 基于 RAG（检索增强生成）架构的知识库问答服务。
 * 处理流程：
 * <ol>
 *     <li>从向量数据库检索相关文档片段</li>
 *     <li>将检索结果作为上下文传递给大语言模型</li>
 *     <li>生成基于事实的答案</li>
 *     <li>组装引用来源信息</li>
 * </ol>
 *
 * @see KnowledgeChatClient 知识库聊天客户端
 * @see CitationAssembler 引用组装器
 */
@Service
@RequiredArgsConstructor
public class KnowledgeQaService {

    /**
     * 当知识库中没有足够证据时的默认响应
     */
    public static final String NO_EVIDENCE_RESPONSE =
        "当前知识库中没有足够证据回答该问题。";

    private final KnowledgeChatClient chatClient;
    private final CitationAssembler citationAssembler;
    private final RagProperties ragProperties;

    public KnowledgeQaResult answer(String question, String sessionId,
                                    String knowledgeBaseId, Long documentId) {
        String normalizedKnowledgeBaseId = normalizeKnowledgeBaseId(knowledgeBaseId);
        try {
            KnowledgeChatClient.KnowledgeAnswer result = chatClient.answer(
                question, sessionId, normalizedKnowledgeBaseId, documentId);
            if (result.documents().isEmpty()) {
                return new KnowledgeQaResult(NO_EVIDENCE_RESPONSE, List.of());
            }
            return new KnowledgeQaResult(result.content(), citationAssembler.assemble(
                result.documents(), ragProperties.getMaxContextDocuments()));
        } catch (AiServiceException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw new AiServiceException("Knowledge AI request failed", failure);
        }
    }

    public Flux<KnowledgeQaResult> stream(String question, String sessionId,
                                          String knowledgeBaseId, Long documentId) {
        String normalizedKnowledgeBaseId = normalizeKnowledgeBaseId(knowledgeBaseId);
        return mapStream(chatClient.stream(question, sessionId, normalizedKnowledgeBaseId, documentId));
    }

    /**
     * 回答知识库问题
     * <p>
     * 根据问题从向量数据库检索相关文档，结合上下文生成答案。
     * 如果没有检索到相关文档，返回默认的无证据响应。
     *
     * @param question        用户问题
     * @param sessionId       会话ID，用于多轮对话记忆
     * @param knowledgeBaseId 知识库ID，用于数据隔离
     * @param documentId      文档ID（可选），限定检索范围
     * @param accessibleDocIds 用户可访问的文档ID列表（用于权限过滤）
     * @return 问答结果，包含答案和引用来源
     * @throws AiServiceException AI 服务调用失败时抛出
     */
    public KnowledgeQaResult answer(
        String question,
        String sessionId,
        String knowledgeBaseId,
        Long documentId,
        List<Long> accessibleDocIds
    ) {
        String normalizedKnowledgeBaseId = normalizeKnowledgeBaseId(knowledgeBaseId);
        KnowledgeChatClient.KnowledgeAnswer knowledgeAnswer;
        try {
            knowledgeAnswer = chatClient.answer(
                question,
                sessionId,
                normalizedKnowledgeBaseId,
                documentId,
                accessibleDocIds
            );
        } catch (AiServiceException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw new AiServiceException("Knowledge AI request failed", failure);
        }
        if (knowledgeAnswer.documents().isEmpty()) {
            return new KnowledgeQaResult(NO_EVIDENCE_RESPONSE, List.of());
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
     * 流式回答知识库问题
     * <p>
     * 以流式方式返回答案，支持实时显示生成过程。
     * <p>
     * 调用链路：
     * <ol>
     *     <li>规范化知识库ID（空值返回 "default"）</li>
     *     <li>调用 SpringAiKnowledgeChatClient.stream() 进行流式生成</li>
     *     <li>SpringAiKnowledgeChatClient 构建 RAG Advisor：
     *       <ul>
     *         <li>构建 Filter 过滤条件（knowledgeBaseId + documentStatus + 权限过滤）</li>
     *         <li>使用 VectorStoreDocumentRetriever 从 rag_vector_store 表检索相关文档</li>
     *         <li>使用 ContextualQueryAugmenter 将检索结果作为上下文注入到提示词</li>
     *       </ul>
     *     </li>
     *     <li>调用 Spring AI ChatClient 进行流式生成（调用通义千问 API）</li>
     *     <li>从第一个响应中提取引用信息，后续响应复用</li>
     *     <li>组装 CitationDTO 列表</li>
     * </ol>
     *
     * @param question        用户问题
     * @param sessionId       会话ID，用于多轮对话记忆
     * @param knowledgeBaseId 知识库ID，用于数据隔离
     * @param documentId      文档ID（可选），限定检索范围
     * @param accessibleDocIds 用户可访问的文档ID列表（用于权限过滤）
     * @return 问答结果流，每个元素包含答案片段和引用来源
     */
    public Flux<KnowledgeQaResult> stream(
        String question,
        String sessionId,
        String knowledgeBaseId,
        Long documentId,
        List<Long> accessibleDocIds
    ) {
        String normalizedKnowledgeBaseId = normalizeKnowledgeBaseId(knowledgeBaseId);
        return mapStream(Flux.defer(() -> chatClient.stream(
                question,
                sessionId,
                normalizedKnowledgeBaseId,
                documentId,
                accessibleDocIds
            )));
    }

    /**
     * 处理流式响应
     * <p>
     * 从第一个响应中提取引用信息，后续响应复用该引用列表。
     * 这样可以避免在每次响应中都携带完整的引用列表，减少数据传输量。
     * <p>
     * 处理逻辑：
     * <ul>
     *     <li>如果第一个响应为空（无信号），则返回空引用列表</li>
     *     <li>如果第一个响应的文档列表为空，返回默认无证据响应</li>
     *     <li>否则，从第一个响应中提取引用，后续响应复用该引用列表</li>
     * </ul>
     *
     * @param source 原始的 KnowledgeAnswer 流（来自 SpringAiKnowledgeChatClient）
     * @return 转换后的 KnowledgeQaResult 流（包含答案和引用）
     */
    private Flux<KnowledgeQaResult> mapStream(Flux<KnowledgeChatClient.KnowledgeAnswer> source) {
        return source
            .switchOnFirst((signal, answers) -> {
                if (!signal.hasValue()) {
                    return answers.map(answer -> new KnowledgeQaResult(
                        answer.content(),
                        List.of()
                    ));
                }
                KnowledgeChatClient.KnowledgeAnswer first = signal.get();
                if (first.documents().isEmpty()) {
                    return Flux.just(new KnowledgeQaResult(NO_EVIDENCE_RESPONSE, List.of()));
                }
                List<CitationDTO> citations = citationAssembler.assemble(
                    first.documents(),
                    ragProperties.getMaxContextDocuments()
                );
                return answers.map(answer -> new KnowledgeQaResult(answer.content(), citations));
            })
            .onErrorMap(
                failure -> !(failure instanceof AiServiceException),
                failure -> new AiServiceException("Knowledge AI request failed", failure)
            );
    }

    /**
     * 规范化知识库ID
     * <p>
     * 如果知识库ID为空或空白，返回默认值 "default"。
     */
    private String normalizeKnowledgeBaseId(String knowledgeBaseId) {
        return knowledgeBaseId == null || knowledgeBaseId.isBlank()
            ? "default"
            : knowledgeBaseId.trim();
    }
}