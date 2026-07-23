package com.hqc.smrag.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 聊天请求 DTO
 * <p>
 * 封装用户发起的聊天请求，包括：
 * <ul>
 *     <li>sessionId：会话ID，用于多轮对话</li>
 *     <li>question：用户问题</li>
 *     <li>userId：用户ID（可选）</li>
 *     <li>knowledgeBaseId：知识库ID（可选）</li>
 *     <li>documentId：文档ID（可选），限定检索范围</li>
 * </ul>
 *
 * @see ChatResponseDTO 聊天响应 DTO
 */
@Data
public class ChatRequestDTO {

    /**
     * 会话ID（必填）
     * <p>
     * 用于标识多轮对话的上下文，最大长度 128 字符。
     */
    @NotBlank
    @Size(max = 128)
    private String sessionId;

    /**
     * 用户问题（必填）
     * <p>
     * 用户提出的自然语言问题，最大长度 4000 字符。
     */
    @NotBlank
    @Size(max = 4000)
    private String question;

    /**
     * 用户ID（可选）
     * <p>
     * 用于用户身份识别和审计。
     */
    @Positive
    private Long userId;

    /**
     * 知识库ID（可选）
     * <p>
     * 指定检索的知识库稳定 code，默认为 "company-main"。
     */
    @Size(max = 128)
    private String knowledgeBaseId;

    /**
     * 文档ID（可选）
     * <p>
     * 限定检索范围为指定文档。
     */
    @Positive
    private Long documentId;
}
