package com.hqc.smrag.service.chat;

import com.hqc.smrag.entity.ChatSession;
import com.hqc.smrag.mapper.ChatSessionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuestionRewriteService {

    private final ChatClient.Builder chatClientBuilder;
    private final ChatSessionMapper sessionMapper;

    @Value("classpath:prompts/rewrite_prompt.txt")
    private Resource rewritePromptResource;

    public String rewrite(String question, String sessionId) {
        // 获取历史会话
        List<ChatSession> history = sessionMapper.findBySessionId(sessionId);
        String historyText = history.stream()
            .map(s -> "问：" + s.getQuestion() + "\n答：" + s.getAnswer())
            .collect(Collectors.joining("\n"));

        if (historyText.isEmpty()) {
            return question;
        }

        try {
            String promptTemplate = rewritePromptResource.getContentAsString();
            String prompt = promptTemplate
                .replace("{history}", historyText)
                .replace("{question}", question);

            ChatClient chatClient = chatClientBuilder.build();
            String rewritten = chatClient.prompt(prompt).call().content();

            log.info("问题改写: {} -> {}", question, rewritten);
            return rewritten;
        } catch (Exception e) {
            log.error("问题改写失败", e);
            return question;
        }
    }
}