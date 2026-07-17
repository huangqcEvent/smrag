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

@Service
public class BusinessToolChatService {

    private static final String SYSTEM_PROMPT = """
        You are an enterprise fund information assistant.
        Use the registered tool for factual data and do not invent product or manager details.
        Answer in the user's language. Describe returns as historical data, never as a promise.
        """;

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
