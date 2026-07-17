package com.hqc.smrag.service.chat;

import com.hqc.smrag.dto.RouteDecision;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Spring AI 路由决策客户端实现
 * <p>
 * 使用大语言模型对用户问题进行意图识别，判断应该由哪个分支处理。
 * 通过结构化输出（Structured Output）返回 {@link RouteDecision} 对象。
 * <p>
 * 提示词模板位于：classpath:prompts/structured_route_prompt.txt
 *
 * @see RouteDecisionClient 接口定义
 * @see RouteDecision 路由决策结果 DTO
 */
@Component
public class SpringAiRouteDecisionClient implements RouteDecisionClient {

    private final ChatClient chatClient;
    private final Resource routePrompt;

    public SpringAiRouteDecisionClient(
        ChatClient.Builder chatClientBuilder,
        @Value("classpath:prompts/structured_route_prompt.txt") Resource routePrompt
    ) {
        this.chatClient = chatClientBuilder.clone().build();
        this.routePrompt = routePrompt;
    }

    /**
     * 对问题进行路由决策
     * <p>
     * 调用大语言模型，根据系统提示词判断问题意图，
     * 并以结构化格式返回决策结果。
     *
     * @param question 用户问题
     * @return 路由决策结果，包含路由类型和决策原因
     */
    @Override
    public RouteDecision decide(String question) {
        return chatClient.prompt()
            .system(readPrompt())
            .user(question)
            .call()
            .entity(RouteDecision.class);
    }

    /**
     * 读取路由提示词模板
     *
     * @return 提示词内容
     * @throws IllegalStateException 无法读取提示词时抛出
     */
    private String readPrompt() {
        try {
            return routePrompt.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read structured route prompt", exception);
        }
    }
}
