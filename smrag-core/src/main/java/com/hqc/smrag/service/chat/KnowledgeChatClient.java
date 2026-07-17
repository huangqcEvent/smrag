package com.hqc.smrag.service.chat;

import org.springframework.ai.document.Document;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 知识库聊天客户端接口
 * <p>
 * 定义基于知识库的问答能力，支持：
 * <ul>
 *     <li>多知识库隔离：通过 knowledgeBaseId 区分不同知识库</li>
 *     <li>文档级别限定：通过 documentId 限定检索范围</li>
 *     <li>多轮对话记忆：通过 sessionId 维护对话上下文</li>
 * </ul>
 *
 * @see SpringAiKnowledgeChatClient Spring AI 实现类
 */
public interface KnowledgeChatClient {

    /**
     * 基于知识库回答问题
     *
     * @param question        用户问题
     * @param sessionId       会话ID，用于多轮对话记忆
     * @param knowledgeBaseId 知识库ID，用于数据隔离
     * @param documentId      文档ID（可选），限定检索范围
     * @return 知识库答案，包含答案内容和引用的文档列表
     */
    KnowledgeAnswer answer(
        String question,
        String sessionId,
        String knowledgeBaseId,
        Long documentId
    );

    Flux<KnowledgeAnswer> stream(
        String question,
        String sessionId,
        String knowledgeBaseId,
        Long documentId
    );

    /**
     * 知识库答案记录类
     * <p>
     * 封装大语言模型的答案内容和引用的文档列表。
     */
    record KnowledgeAnswer(String content, List<Document> documents) {
        public KnowledgeAnswer {
            content = content == null ? "" : content;
            documents = documents == null ? List.of() : List.copyOf(documents);
        }
    }
}
