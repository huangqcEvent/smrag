package com.hqc.smrag.service.chat;

import com.hqc.smrag.exception.AiServiceException;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 销售话术生成服务
 * <p>
 * 基于合规要求生成基金销售话术，确保：
 * <ul>
 *     <li>不承诺收益，不将历史业绩描述为未来保证</li>
 *     <li>必须披露风险，说明产品可能产生损失</li>
 *     <li>建议阅读正式产品材料</li>
 *     <li>根据客户风险承受能力谨慎决策</li>
 *     <li>仅生成话术，不虚构产品事实或客户信息</li>
 * </ul>
 *
 * @see RouteType#SALES_SCRIPT 销售话术路由类型
 */
@Service
public class SalesScriptService {

    /**
     * 系统提示词
     * <p>
     * 定义销售话术生成的合规要求：
     * <ul>
     *     <li>不承诺收益</li>
     *     <li>披露风险</li>
     *     <li>建议阅读产品材料</li>
     *     <li>不虚构事实</li>
     * </ul>
     */
    private static final String SYSTEM_PROMPT = """
        你是一名审慎、合规的基金销售话术助手。
        不承诺收益，不将历史业绩描述为未来保证。
        必须披露风险，说明产品可能产生损失。
        建议阅读正式产品材料，并根据客户风险承受能力谨慎决策。
        仅生成话术，不虚构产品事实或客户信息。
        """;

    private final ChatClient chatClient;
    private final ChatMemory chatMemory;

    @Autowired
    public SalesScriptService(ChatClient.Builder chatClientBuilder, ChatMemory chatMemory) {
        this(chatClientBuilder.clone().build(), chatMemory);
    }

    SalesScriptService(ChatClient chatClient, ChatMemory chatMemory) {
        this.chatClient = chatClient;
        this.chatMemory = chatMemory;
    }

    /**
     * 生成销售话术
     * <p>
     * 根据用户请求和可选的客户画像，生成合规的销售话术。
     *
     * @param question        用户请求（如产品介绍、风险提示等）
     * @param customerProfile 客户画像（可选），用于个性化话术
     * @param sessionId       会话ID，用于多轮对话记忆
     * @return 生成的销售话术
     * @throws AiServiceException AI 服务调用失败时抛出
     */
    public String generate(String question, String customerProfile, String sessionId) {
        String profile = customerProfile == null || customerProfile.isBlank()
            ? "No customer profile was provided."
            : customerProfile.trim();
        String userPrompt = "Request:\n" + question.trim() + "\n\nCustomer profile:\n" + profile;
        try {
            return chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .messages(history(sessionId))
                .user(userPrompt)
                .call()
                .content();
        } catch (RuntimeException failure) {
            throw new AiServiceException("Sales script AI request failed", failure);
        }
    }

    public Flux<String> stream(String question, String customerProfile, String sessionId) {
        String profile = customerProfile == null || customerProfile.isBlank()
            ? "No customer profile was provided."
            : customerProfile.trim();
        String userPrompt = "Request:\n" + question.trim() + "\n\nCustomer profile:\n" + profile;
        return Flux.defer(() -> chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .messages(history(sessionId))
                .user(userPrompt)
                .stream()
                .content())
            .onErrorMap(
                failure -> !(failure instanceof AiServiceException),
                failure -> new AiServiceException("Sales script AI request failed", failure)
            );
    }

    private List<Message> history(String sessionId) {
        List<Message> messages = chatMemory.get(sessionId);
        return messages == null ? List.of() : List.copyOf(messages);
    }

    /**
     * 获取系统提示词（供测试使用）
     *
     * @return 系统提示词内容
     */
    static String systemPrompt() {
        return SYSTEM_PROMPT;
    }
}
