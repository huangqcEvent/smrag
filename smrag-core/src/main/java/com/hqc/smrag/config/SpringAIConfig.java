package com.hqc.smrag.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;
import org.springframework.ai.rag.preretrieval.query.transformation.RewriteQueryTransformer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SpringAIConfig {

    @Bean
    public ChatMemory chatMemory(
        ChatMemoryRepository repository,
        RagProperties properties
    ) {
        return MessageWindowChatMemory.builder()
            .chatMemoryRepository(repository)
            .maxMessages(properties.getMemoryWindow())
            .build();
    }

    @Bean
    public ChatClient.Builder chatClientBuilder(ChatModel chatModel) {
        return ChatClient.builder(chatModel);
    }

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder.clone().build();
    }

    @Bean
    public QueryTransformer rewriteQueryTransformer(ChatClient.Builder builder) {
        return RewriteQueryTransformer.builder()
            .chatClientBuilder(builder.clone())
            .targetSearchSystem("enterprise knowledge base")
            .build();
    }
}
