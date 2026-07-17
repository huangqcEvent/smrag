package com.hqc.smrag.service.chat;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 对话查询服务
 * <p>
 * 负责将用户问题进行重写，使其更适合知识库检索。
 * 重写过程会结合历史对话上下文，将口语化的表达转换为更清晰、更具搜索价值的查询。
 * <p>
 * 例如：
 * <ul>
 *     <li>"它怎么样" → 重写为具体的产品名称和查询意图</li>
 *     <li>"上一个产品的收益" → 重写为具体的产品名称和时间范围</li>
 * </ul>
 *
 * @see QueryTransformer 查询转换器
 * @see ChatMemoryService 对话记忆服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationQueryService {

    private final QueryTransformer queryTransformer;
    private final ChatMemoryService memoryService;

    /**
     * 重写用户问题
     * <p>
     * 如果会话存在历史对话，则结合上下文进行重写；
     * 否则直接返回原始问题（去除首尾空白）。
     * <p>
     * 重写失败时，记录警告日志并返回原始问题，确保服务可用性。
     *
     * @param question  用户原始问题
     * @param sessionId 会话ID
     * @return 重写后的问题
     * @throws IllegalArgumentException 问题为空时抛出
     */
    public String rewrite(String question, String sessionId) {
        String normalizedQuestion = requireQuestion(question);
        List<Message> history = memoryService.history(sessionId);
        if (history.isEmpty()) {
            return normalizedQuestion;
        }

        try {
            Query rewritten = queryTransformer.transform(
                Query.builder()
                    .text(normalizedQuestion)
                    .history(history)
                    .build()
            );
            if (rewritten == null || rewritten.text() == null || rewritten.text().isBlank()) {
                return normalizedQuestion;
            }
            return rewritten.text().trim();
        } catch (RuntimeException failure) {
            log.warn("Unable to rewrite conversation query; using the original question", failure);
            return normalizedQuestion;
        }
    }

    /**
     * 校验问题非空
     */
    private String requireQuestion(String question) {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("question must not be blank");
        }
        return question.trim();
    }
}
