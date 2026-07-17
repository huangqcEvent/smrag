package com.hqc.smrag.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 聊天会话实体
 * <p>
 * 对应数据库表 chat_session，存储用户的对话记录。
 * 每条记录对应一次问答交互，包括：
 * <ul>
 *     <li>用户问题（原始和重写后）</li>
 *     <li>模型答案</li>
 *     <li>路由类型</li>
 *     <li>引用来源</li>
 * </ul>
 * <p>
 * 主要字段：
 * <ul>
 *     <li>id：主键</li>
 *     <li>sessionId：会话ID，关联多轮对话</li>
 *     <li>userId：用户ID</li>
 *     <li>question：用户原始问题</li>
 *     <li>rewriteQuestion：重写后的问题</li>
 *     <li>answer：模型答案</li>
 *     <li>routeType：路由类型</li>
 *     <li>referenceDocs：引用来源（JSON格式）</li>
 * </ul>
 *
 * @see com.hqc.smrag.service.chat.ChatSessionPersistenceService 会话持久化服务
 */
@Data
@TableName("chat_session")
public class ChatSession {

    /**
     * 主键ID（自增）
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 会话ID
     * <p>
     * 用于关联多轮对话，同一个会话ID下的消息按时间顺序排列。
     */
    private String sessionId;

    /**
     * 用户ID
     * <p>
     * 用于用户身份识别和审计。
     */
    private Long userId;

    /**
     * 用户原始问题
     */
    private String question;

    /**
     * 重写后的问题
     * <p>
     * 结合历史对话上下文，将口语化问题重写为更清晰的查询。
     */
    private String rewriteQuestion;

    /**
     * 模型答案
     */
    private String answer;

    /**
     * 路由类型
     * <p>
     * 表示问题被分发到哪个分支处理。
     */
    private String routeType;

    /**
     * 引用来源
     * <p>
     * JSON 格式的引用列表，包含文档名称、页码、摘要等信息。
     */
    private String referenceDocs;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}