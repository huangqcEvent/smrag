package com.hqc.smrag.service.chat;

import com.hqc.smrag.enums.RouteType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Slf4j
@Service
@RequiredArgsConstructor
public class RouteService {

    private final ChatClient chatClient;

    @Value("classpath:prompts/route_prompt.txt")
    private Resource routePromptResource;

    public RouteType route(String question) {
        try {
            String promptTemplate = routePromptResource.getContentAsString(StandardCharsets.UTF_8);
            String prompt = promptTemplate.replace("{question}", question);

            String result = chatClient.prompt().user(prompt).call().content().trim();

            try {
                RouteType type = RouteType.valueOf(result);
                log.info("路由分类: {} -> {}", question, type);
                return type;
            } catch (IllegalArgumentException e) {
                log.warn("路由分类失败，默认返回KNOWLEDGE_QA: {}", result);
                return RouteType.KNOWLEDGE_QA;
            }
        } catch (Exception e) {
            log.error("路由分类异常", e);
            return RouteType.KNOWLEDGE_QA;
        }
    }
}