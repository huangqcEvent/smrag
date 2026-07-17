package com.hqc.smrag.service.chat;

import com.hqc.smrag.dto.RouteDecision;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

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

    @Override
    public RouteDecision decide(String question) {
        return chatClient.prompt()
            .system(readPrompt())
            .user(question)
            .call()
            .entity(RouteDecision.class);
    }

    private String readPrompt() {
        try {
            return routePrompt.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read structured route prompt", exception);
        }
    }
}
