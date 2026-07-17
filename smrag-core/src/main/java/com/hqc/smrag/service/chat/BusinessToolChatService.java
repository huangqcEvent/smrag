package com.hqc.smrag.service.chat;

import com.hqc.smrag.enums.RouteType;
import com.hqc.smrag.exception.AiServiceException;
import com.hqc.smrag.service.tool.FundTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Map;

/**
 * 业务工具聊天服务
 * <p>
 * 处理与基金产品、基金经理相关的业务查询，通过工具调用（Function Calling）获取数据。
 * 支持的业务类型：
 * <ul>
 *     <li>PRODUCT_QUERY：产品查询，调用 queryProduct 工具</li>
 *     <li>PRODUCT_COMPARE：产品对比，调用 compareProducts 工具</li>
 *     <li>PRODUCT_RECOMMEND：产品推荐，调用 recommendProducts 工具</li>
 *     <li>MANAGER_QUERY：基金经理查询，调用 queryManager 工具</li>
 * </ul>
 *
 * @see FundTools 基金工具类
 * @see RouteType 路由类型枚举
 */
@Service
public class BusinessToolChatService {

    /**
     * 系统提示词
     * <p>
     * 指导模型：
     * <ul>
     *     <li>使用注册的工具获取事实数据</li>
     *     <li>不虚构产品或经理信息</li>
     *     <li>使用用户语言回答</li>
     *     <li>将收益描述为历史数据，而非承诺</li>
     * </ul>
     */
    private static final String SYSTEM_PROMPT = """
        You are an enterprise fund information assistant.
        Use the registered tool for factual data and do not invent product or manager details.
        Answer in the user's language. Describe returns as historical data, never as a promise.
        """;

    /**
     * 路由类型与工具名称的映射关系
     */
    private static final Map<RouteType, String> TOOL_NAMES = Map.of(
        RouteType.PRODUCT_QUERY, "queryProduct",
        RouteType.PRODUCT_COMPARE, "compareProducts",
        RouteType.PRODUCT_RECOMMEND, "recommendProducts",
        RouteType.MANAGER_QUERY, "queryManager"
    );

    private final ChatClient chatClient;
    private final ChatMemory chatMemory;
    private final ToolCallback[] toolCallbacks;

    @Autowired
    public BusinessToolChatService(
        ChatClient.Builder chatClientBuilder,
        ChatMemory chatMemory,
        FundTools fundTools
    ) {
        this(chatClientBuilder.clone().build(), chatMemory, fundTools);
    }

    BusinessToolChatService(ChatClient chatClient, ChatMemory chatMemory, FundTools fundTools) {
        this.chatClient = chatClient;
        this.chatMemory = chatMemory;
        this.toolCallbacks = ToolCallbacks.from(fundTools);
    }

    /**
     * 处理业务查询
     * <p>
     * 根据路由类型选择对应的工具，调用大语言模型处理用户问题。
     * 模型会自动决定是否需要调用工具来获取数据。
     *
     * @param routeType 路由类型
     * @param question  用户问题
     * @param sessionId 会话ID，用于多轮对话记忆
     * @return 业务查询答案
     * @throws AiServiceException AI 服务调用失败时抛出
     */
    public String answer(RouteType routeType, String question, String sessionId) {
        ToolCallback selectedTool = selectedTool(routeType);
        try {
            return chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(question)
                .advisors(MessageChatMemoryAdvisor.builder(chatMemory)
                    .conversationId(sessionId)
                    .build())
                .toolCallbacks(selectedTool)
                .call()
                .content();
        } catch (RuntimeException failure) {
            throw new AiServiceException("Business AI request failed", failure);
        }
    }

    /**
     * 根据路由类型选择对应的工具
     *
     * @param routeType 路由类型
     * @return 工具回调
     * @throws IllegalArgumentException 路由类型不支持业务工具时抛出
     * @throws IllegalStateException     找不到对应的工具回调时抛出
     */
    ToolCallback selectedTool(RouteType routeType) {
        String toolName = TOOL_NAMES.get(routeType);
        if (toolName == null) {
            throw new IllegalArgumentException("Route does not support business tools: " + routeType);
        }
        return Arrays.stream(toolCallbacks)
            .filter(callback -> callback.getToolDefinition().name().equals(toolName))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Missing tool callback: " + routeType));
    }
}
