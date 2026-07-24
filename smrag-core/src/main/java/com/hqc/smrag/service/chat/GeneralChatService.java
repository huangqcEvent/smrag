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
 * Handles general questions that do not require private knowledge or business tools.
 */
@Service
public class GeneralChatService {

    private static final String SYSTEM_PROMPT = """
        You are FundRAG's general-purpose assistant.
        Answer directly in the user's language.
        You have no access to private knowledge bases, business databases,
        external tools, the internet, or real-time data. Never claim otherwise.
        State uncertainty when facts may have changed, and never invent citations,
        fund products, or manager details.
        For investments, provide general educational information and never promise returns
        or present uncertain personalized advice as fact.
        """;

    private final ChatClient chatClient;
    private final ChatMemory chatMemory;

    @Autowired
    public GeneralChatService(
        ChatClient.Builder chatClientBuilder,
        ChatMemory chatMemory
    ) {
        this(chatClientBuilder.clone().build(), chatMemory);
    }

    GeneralChatService(ChatClient chatClient, ChatMemory chatMemory) {
        this.chatClient = chatClient;
        this.chatMemory = chatMemory;
    }

    /**
     * Generates a direct answer without registering retrieval or tool callbacks.
     */
    public String answer(String question, String conversationKey) {
        try {
            return chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .messages(history(conversationKey))
                .user(question)
                .call()
                .content();
        } catch (RuntimeException failure) {
            throw new AiServiceException("General chat AI request failed", failure);
        }
    }

    /**
     * Streams a direct answer without registering retrieval or tool callbacks.
     */
    public Flux<String> stream(String question, String conversationKey) {
        return Flux.defer(() -> chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .messages(history(conversationKey))
                .user(question)
                .stream()
                .content())
            .onErrorMap(
                failure -> !(failure instanceof AiServiceException),
                failure -> new AiServiceException(
                    "General chat AI request failed",
                    failure
                )
            );
    }

    private List<Message> history(String conversationKey) {
        List<Message> messages = chatMemory.get(conversationKey);
        return messages == null ? List.of() : List.copyOf(messages);
    }

    static String systemPrompt() {
        return SYSTEM_PROMPT;
    }
}
