package com.hqc.smrag.config;

import com.hqc.smrag.service.tool.*;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class SpringAIConfig {

    private final ProductQueryTool productQueryTool;
    private final ManagerQueryTool managerQueryTool;
    private final ProductCompareTool productCompareTool;
    private final ProductRecommendTool productRecommendTool;

    @Bean
    public ChatClient.Builder chatClientBuilder() {
        return ChatClient.builder();
    }
}