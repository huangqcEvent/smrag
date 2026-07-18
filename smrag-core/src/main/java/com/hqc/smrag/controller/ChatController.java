package com.hqc.smrag.controller;

import com.hqc.smrag.common.Result;
import com.hqc.smrag.dto.ChatRequestDTO;
import com.hqc.smrag.dto.ChatResponseDTO;
import com.hqc.smrag.dto.ChatStreamEvent;
import com.hqc.smrag.entity.ChatSession;
import com.hqc.smrag.service.chat.AiChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

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
     * @param userId  当前用户ID（从 JWT 中获取）
     * @return 聊天响应，包含答案、引用来源、路由类型等
     */
    @PostMapping("/ask")
    public Result<ChatResponseDTO> ask(
        @Valid @RequestBody ChatRequestDTO request,
        @AuthenticationPrincipal Long userId
    ) {
        // 设置当前用户ID，用于权限过滤
        request.setUserId(userId);
        return Result.success(aiChatService.ask(request));
    }

    /**
     * 流式智能问答接口
     * <p>
     * 与 {@link #ask} 接口功能相同，但以 Server-Sent Events (SSE) 流式方式返回结果。
     * 客户端可以实时接收生成过程中的各个阶段事件，实现打字机效果。
     * <p>
     * 完整调用链路：
     * <ol>
     *     <li>ChatController.stream() 接收请求，注入用户ID
     *       <ul>
     *         <li>从 JWT 中提取 userId</li>
     *         <li>设置到 ChatRequestDTO 中</li>
     *       </ul>
     *     </li>
     *     <li>AiChatService.stream() 流式问答处理入口
     *       <ul>
     *         <li>规范化请求参数</li>
     *         <li>获取用户可访问的文档ID列表（权限过滤）</li>
     *         <li>发送 ANALYZING 状态事件</li>
     *         <li>异步执行问题重写和路由决策</li>
     *       </ul>
     *     </li>
     *     <li>ConversationQueryService.rewrite() 问题重写
     *       <ul>
     *         <li>结合历史对话上下文</li>
     *         <li>将口语化问题重写为更清晰的查询</li>
     *       </ul>
     *     </li>
     *     <li>RouteService.route() 路由决策
     *       <ul>
     *         <li>判断问题类型（KNOWLEDGE_QA、PRODUCT_QUERY、SALES_SCRIPT 等）</li>
     *       </ul>
     *     </li>
     *     <li>KnowledgeQaService.stream() 知识库问答（以 KNOWLEDGE_QA 为例）
     *       <ul>
     *         <li>规范化知识库ID</li>
     *         <li>调用 SpringAiKnowledgeChatClient.stream()</li>
     *       </ul>
     *     </li>
     *     <li>SpringAiKnowledgeChatClient.stream() Spring AI 流式生成
     *       <ul>
     *         <li>构建 RAG Advisor（包含向量检索器和上下文增强器）</li>
     *         <li>构建 ChatClient 请求（系统提示词 + 历史对话 + 用户问题 + RAG Advisor）</li>
     *       </ul>
     *     </li>
     *     <li>RetrievalAugmentationAdvisor 执行 RAG 流程
     *       <ul>
     *         <li>VectorStoreDocumentRetriever 从 rag_vector_store 表检索相关文档
     *           <ul>
     *             <li>应用过滤条件：knowledgeBaseId + documentStatus + 权限过滤</li>
     *             <li>计算向量相似度，返回 Top-K 文档片段</li>
     *           </ul>
     *         </li>
     *         <li>ContextualQueryAugmenter 将检索结果注入到提示词
     *           <ul>
     *             <li>如果没有检索到文档，返回默认提示词</li>
     *             <li>否则，将文档内容作为上下文添加到提示词</li>
     *           </ul>
     *         </li>
     *       </ul>
     *     </li>
     *     <li>Spring AI ChatClient 调用通义千问 API
     *       <ul>
     *         <li>发送流式请求到通义千问（qwen-plus）</li>
     *         <li>返回流式响应</li>
     *       </ul>
     *     </li>
     *     <li>SpringAiKnowledgeChatClient 提取引用文档
     *       <ul>
     *         <li>从响应上下文中提取 DOCUMENT_CONTEXT</li>
     *         <li>转换为 KnowledgeAnswer（包含答案片段和引用文档）</li>
     *       </ul>
     *     </li>
     *     <li>KnowledgeQaService.mapStream() 处理流式响应
     *       <ul>
     *         <li>从第一个响应中提取引用信息</li>
     *         <li>后续响应复用该引用列表</li>
     *         <li>组装 CitationDTO 列表</li>
     *       </ul>
     *     </li>
     *     <li>AiChatService.streamPrepared() 发送 SSE 事件
     *       <ul>
     *         <li>发送 metadata 事件（sessionId、routeType）</li>
     *         <li>发送 status 事件（RETRIEVING）</li>
     *         <li>发送多个 delta 事件（答案片段）</li>
     *         <li>发送 citations 事件（引用来源）</li>
     *         <li>发送 done 事件（生成完成）</li>
     *       </ul>
     *     </li>
     *     <li>AiChatService.persistAndComplete() 持久化对话记录
     *       <ul>
     *         <li>构造 ChatSession 实体</li>
     *         <li>保存到 chat_session 表</li>
     *         <li>更新对话记忆（ChatMemory）</li>
     *       </ul>
     *     </li>
     * </ol>
     * <p>
     * SSE 事件类型：
     * <ul>
     *     <li>metadata：会话元数据（sessionId、routeType）</li>
     *     <li>status：处理状态（ANALYZING、RETRIEVING、GENERATING 等）</li>
     *     <li>delta：答案片段，增量文本内容</li>
     *     <li>citations：引用来源列表</li>
     *     <li>done：生成完成</li>
     *     <li>error：处理错误</li>
     * </ul>
     * <p>
     * 使用示例：
     * <pre>
     * const eventSource = new EventSource('/api/chat/stream', {
     *     method: 'POST',
     *     body: JSON.stringify({ question: '...', sessionId: '...' }),
     *     headers: { 'Content-Type': 'application/json' }
     * });
     * eventSource.addEventListener('delta', e => {
     *     const data = JSON.parse(e.data);
     *     console.log('收到片段:', data.content);
     * });
     * </pre>
     *
     * @param request 聊天请求，包含问题、会话ID、知识库ID等
     * @param userId  当前用户ID（从 JWT 中获取）
     * @return SSE 事件流，包含处理过程的各个阶段事件
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<ChatStreamEvent>> stream(
        @Valid @RequestBody ChatRequestDTO request,
        @AuthenticationPrincipal Long userId
    ) {
        // 设置当前用户ID，用于权限过滤
        request.setUserId(userId);
        return aiChatService.stream(request)
            .map(event -> ServerSentEvent.<ChatStreamEvent>builder(event)
                .event(event.type())
                .build());
    }

    /**
     * 查询对话历史
     * <p>
     * 返回指定会话ID下的所有历史对话记录，按时间顺序排列。
     * 只能查询当前用户的对话记录。
     *
     * @param sessionId 会话ID
     * @param userId    当前用户ID（从 JWT 中获取）
     * @return 该会话下的所有对话记录列表
     */
    @GetMapping("/history/{sessionId}")
    public Result<List<ChatSession>> history(
        @PathVariable String sessionId,
        @AuthenticationPrincipal Long userId
    ) {
        return Result.success(aiChatService.history(sessionId, userId));
    }

    /**
     * 查询用户的所有会话列表
     * <p>
     * 返回当前用户的所有会话，按最后更新时间倒序排列。
     *
     * @param userId 当前用户ID（从 JWT 中获取）
     * @return 会话列表
     */
    @GetMapping("/sessions")
    public Result<List<ChatSession>> sessions(@AuthenticationPrincipal Long userId) {
        return Result.success(aiChatService.getUserSessions(userId));
    }
}
