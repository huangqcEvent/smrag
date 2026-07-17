package com.hqc.smrag.controller;

import com.hqc.smrag.common.Result;
import com.hqc.smrag.dto.ChatRequestDTO;
import com.hqc.smrag.dto.ChatResponseDTO;
import com.hqc.smrag.entity.ChatSession;
import com.hqc.smrag.service.chat.AiChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * AI 聊天控制器
 * <p>
 * 提供智能问答相关的 REST API 接口，支持：
 * <ul>
 *     <li>智能问答：根据用户问题路由到不同的处理分支（知识库问答、产品查询、销售话术等）</li>
 *     <li>对话历史：查询指定会话的历史消息</li>
 * </ul>
 *
 * @see AiChatService AI 聊天服务
 * @see ChatRequestDTO  聊天请求 DTO
 * @see ChatResponseDTO 聊天响应 DTO
 */
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final AiChatService aiChatService;

    /**
     * 智能问答接口
     * <p>
     * 接收用户问题，经过路由决策后分发到对应的处理分支：
     * <ul>
     *     <li>KNOWLEDGE_QA：知识库问答，基于 RAG 检索相关文档</li>
     *     <li>PRODUCT_QUERY/COMPARE/RECOMMEND：产品查询、对比、推荐</li>
     *     <li>MANAGER_QUERY：基金经理信息查询</li>
     *     <li>SALES_SCRIPT：销售话术生成</li>
     * </ul>
     *
     * @param request 聊天请求，包含问题、会话ID、知识库ID等
     * @return 聊天响应，包含答案、引用来源、路由类型等
     */
    @PostMapping("/ask")
    public Result<ChatResponseDTO> ask(@Valid @RequestBody ChatRequestDTO request) {
        return Result.success(aiChatService.ask(request));
    }

    /**
     * 查询对话历史
     * <p>
     * 返回指定会话ID下的所有历史对话记录，按时间顺序排列。
     *
     * @param sessionId 会话ID
     * @return 该会话下的所有对话记录列表
     */
    @GetMapping("/history/{sessionId}")
    public Result<List<ChatSession>> history(@PathVariable String sessionId) {
        return Result.success(aiChatService.history(sessionId));
    }
}
