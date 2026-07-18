package com.hqc.smrag.service.chat;

import com.hqc.smrag.entity.ChatSession;
import com.hqc.smrag.mapper.ChatSessionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 对话会话持久化服务
 * <p>
 * 负责对话记录的数据库持久化操作，包括：
 * <ul>
 *     <li>保存对话记录：将问答记录保存到数据库</li>
 *     <li>查询历史记录：查询指定会话的历史对话</li>
 * </ul>
 * <p>
 * 对话记录包括：
 * <ul>
 *     <li>用户问题（原始问题和重写后的问题）</li>
 *     <li>模型答案</li>
 *     <li>路由类型</li>
 *     <li>引用来源</li>
 * </ul>
 *
 * @see ChatSession 对话会话实体
 * @see ChatSessionMapper 对话会话 Mapper
 */
@Service
@RequiredArgsConstructor
public class ChatSessionPersistenceService {

    private final ChatSessionMapper sessionMapper;
    private final ChatMemory chatMemory;

    /**
     * 保存对话记录
     * <p>
     * 将对话记录插入到数据库，用于历史查询和审计。
     *
     * @param session 对话会话实体
     */
    @Transactional
    public void save(ChatSession session) {
        sessionMapper.insert(session);
        chatMemory.add(session.getSessionId(), List.of(
            new UserMessage(session.getQuestion()),
            new AssistantMessage(session.getAnswer())
        ));
    }

    /**
     * 查询指定会话的历史记录
     * <p>
     * 返回该会话下的所有对话记录，按时间顺序排列。
     * 只能查询当前用户的对话记录。
     *
     * @param sessionId 会话ID
     * @param userId    当前用户ID（用于权限验证）
     * @return 对话记录列表
     * @throws IllegalArgumentException 会话ID为空时抛出
     */
    @Transactional(readOnly = true)
    public List<ChatSession> history(String sessionId, Long userId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId must not be blank");
        }
        return sessionMapper.findBySessionIdAndUserId(sessionId.trim(), userId);
    }

    /**
     * 查询用户的所有会话
     * <p>
     * 返回用户的所有会话，按最后更新时间倒序排列。
     *
     * @param userId 用户ID
     * @return 会话列表
     */
    @Transactional(readOnly = true)
    public List<ChatSession> getUserSessions(Long userId) {
        return sessionMapper.findSessionsByUserId(userId);
    }
}
