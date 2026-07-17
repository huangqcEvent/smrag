package com.hqc.smrag.service.chat;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 对话记忆服务
 * <p>
 * 封装 Spring AI 的 ChatMemory 组件，提供对话消息的存取能力。
 * 主要功能：
 * <ul>
 *     <li>查询历史消息：获取指定会话的历史对话记录</li>
 *     <li>添加用户消息：将用户问题添加到对话记忆</li>
 *     <li>添加助手消息：将模型答案添加到对话记忆</li>
 *     <li>清空对话记忆：清除指定会话的所有历史消息</li>
 * </ul>
 * <p>
 * 对话记忆用于：
 * <ul>
 *     <li>多轮对话上下文维护</li>
 *     <li>问题重写（结合历史理解用户意图）</li>
 * </ul>
 *
 * @see ChatMemory Spring AI 对话记忆接口
 * @see MessageChatMemoryAdvisor 对话记忆顾问
 */
@Service
@RequiredArgsConstructor
public class ChatMemoryService {

    private final ChatMemory chatMemory;

    /**
     * 获取指定会话的历史消息
     *
     * @param sessionId 会话ID
     * @return 历史消息列表，按时间顺序排列
     * @throws IllegalArgumentException 会话ID为空时抛出
     */
    public List<Message> history(String sessionId) {
        return chatMemory.get(requireSessionId(sessionId));
    }

    /**
     * 添加用户消息到对话记忆
     *
     * @param sessionId 会话ID
     * @param content   消息内容
     * @throws IllegalArgumentException 会话ID为空时抛出
     */
    public void addUserMessage(String sessionId, String content) {
        chatMemory.add(requireSessionId(sessionId), new UserMessage(content));
    }

    /**
     * 添加助手消息到对话记忆
     *
     * @param sessionId 会话ID
     * @param content   消息内容
     * @throws IllegalArgumentException 会话ID为空时抛出
     */
    public void addAssistantMessage(String sessionId, String content) {
        chatMemory.add(requireSessionId(sessionId), new AssistantMessage(content));
    }

    /**
     * 清空指定会话的对话记忆
     *
     * @param sessionId 会话ID
     * @throws IllegalArgumentException 会话ID为空时抛出
     */
    public void clear(String sessionId) {
        chatMemory.clear(requireSessionId(sessionId));
    }

    /**
     * 校验会话ID非空
     */
    private String requireSessionId(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId must not be blank");
        }
        return sessionId.trim();
    }
}
