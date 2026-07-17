package com.hqc.smrag.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.PromptTemplate;
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
     * 例如将口语化的表达转换为更正式的关键词查询，并保留完整的对话上下文。
     * <p>
     * 重写规则：
     * <ul>
     *     <li>保留对比意图和对比对象（如"A和B对比"而非仅"B对比"）</li>
     *     <li>补充历史对话中的实体名称（如"它"→具体产品名）</li>
     *     <li>生成结构化检索查询，包含产品名、属性、对比关系等关键信息</li>
     * </ul>
     *
     * @param builder ChatClient 构建器
     * @return 查询重写转换器
     */
    @Bean
    public QueryTransformer rewriteQueryTransformer(ChatClient.Builder builder) {
        return RewriteQueryTransformer.builder()
            .chatClientBuilder(builder.clone())
            .promptTemplate(new PromptTemplate("""
                你是一个专业的查询改写助手,帮助用户将对话式问题转换为适合 {target} 的精确查询。

                ## 改写规则

                1. **保留对比完整性**:
                   - 如果用户问题涉及对比(如"A和B对比"、"对比B呢"),必须保留所有对比对象
                   - 示例: "对比稳健货币宝呢" + 历史(讨论均衡成长混合) → "均衡成长混合与稳健货币宝对比分析"

                2. **补充指代对象**:
                   - 将代词(它、这个、那个)替换为历史对话中的具体实体名称
                   - 示例: "它的收益率" + 历史(讨论产品A) → "产品A收益率"

                3. **提取关键信息**:
                   - 产品名称、属性(风险等级、收益率、费率)、对比维度
                   - 示例: "两个产品哪个风险低" → "产品A与产品B风险等级对比"

                4. **保持查询简洁**:
                   - 去除语气词(呢、吧、啊)
                   - 使用关键词组合而非完整句子
                   - 示例: "这款产品的历史业绩怎么样啊" → "产品名称 历史业绩"

                用户问题: {query}

                请直接输出改写后的查询,不要添加解释或说明。
                """))
            .build();
    }
}
