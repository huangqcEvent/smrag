package com.hqc.smrag.service.chat;

import com.hqc.smrag.exception.AiServiceException;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Service;

@Service
public class SalesScriptService {

    private static final String SYSTEM_PROMPT = """
        你是一名审慎、合规的基金销售话术助手。
        不承诺收益，不将历史业绩描述为未来保证。
        必须披露风险，说明产品可能产生损失。
        建议阅读正式产品材料，并根据客户风险承受能力谨慎决策。
        仅生成话术，不虚构产品事实或客户信息。
        """;

    private final ChatClient chatClient;
    private final ChatMemory chatMemory;

    public SalesScriptService(ChatClient.Builder chatClientBuilder, ChatMemory chatMemory) {
        this.chatClient = chatClientBuilder.clone().build();
        this.chatMemory = chatMemory;
    }

    public String generate(String question, String customerProfile, String sessionId) {
        String profile = customerProfile == null || customerProfile.isBlank()
            ? "No customer profile was provided."
            : customerProfile.trim();
        String userPrompt = "Request:\n" + question.trim() + "\n\nCustomer profile:\n" + profile;
        try {
            return chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(userPrompt)
                .advisors(MessageChatMemoryAdvisor.builder(chatMemory)
                    .conversationId(sessionId)
                    .build())
                .call()
                .content();
        } catch (RuntimeException failure) {
            throw new AiServiceException("Sales script AI request failed", failure);
        }
    }

    static String systemPrompt() {
        return SYSTEM_PROMPT;
    }
}
