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

/**
 * Spring AI 配置类
 * <p>
 * 配置 Spring AI 的核心组件，包括：
 * <ul>
 *     <li>ChatMemory：对话记忆，用于维护多轮对话的上下文</li>
 *     <li>ChatClient：聊天客户端，用于与大语言模型交互</li>
 *     <li>QueryTransformer：查询转换器，用于优化用户问题以提升检索效果</li>
 * </ul>
 *
 * @see RagProperties RAG相关配置属性
 */
@Configuration
public class SpringAIConfig {

    /**
     * 配置对话记忆
     * <p>
     * 使用滑动窗口策略保留最近的 N 条消息，避免上下文过长导致 token 消耗过大。
     * 窗口大小由 {@link RagProperties#getMemoryWindow()} 配置。
     *
     * @param repository 对话记忆持久化仓库（JDBC实现）
     * @param properties RAG配置属性
     * @return 配置好的对话记忆实例
     */
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

    /**
     * 配置 ChatClient 构建器
     * <p>
     * 创建一个基于当前 ChatModel 的构建器，可用于构建具备不同特性的聊天客户端。
     *
     * @param chatModel Spring AI 注入的聊天模型（由 Alibaba DashScope 提供）
     * @return ChatClient 构建器
     */
    @Bean
    public ChatClient.Builder chatClientBuilder(ChatModel chatModel) {
        return ChatClient.builder(chatModel);
    }

    /**
     * 配置默认的 ChatClient
     * <p>
     * 提供一个基础的聊天客户端，用于简单的对话场景。
     *
     * @param builder ChatClient 构建器
     * @return 配置好的聊天客户端
     */
    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder.clone().build();
    }

    /**
     * 配置查询重写转换器
     * <p>
     * 在进行知识库检索前，将用户的原始问题重写为更适合检索的形式。
     * 例如将口语化的表达转换为更正式的关键词查询。
     * <p>
     * 目标搜索系统设置为 "enterprise knowledge base"，帮助模型理解重写目标。
     *
     * @param builder ChatClient 构建器
     * @return 查询重写转换器
     */
    @Bean
    public QueryTransformer rewriteQueryTransformer(ChatClient.Builder builder) {
        return RewriteQueryTransformer.builder()
            .chatClientBuilder(builder.clone())
            .targetSearchSystem("enterprise knowledge base")
            .build();
    }
}
