package com.hqc.smrag.service.chat;

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
public class AnswerService {

    private final ChatClient.Builder chatClientBuilder;

    @Value("classpath:prompts/rag_prompt.txt")
    private Resource ragPromptResource;

    public String generate(String question, List<String> contexts) {
        String contextsText = contexts.stream()
            .map(c -> "【片段】" + c)
            .collect(Collectors.joining("\n"));

        try {
            String promptTemplate = ragPromptResource.getContentAsString();
            String prompt = promptTemplate
                .replace("{contexts}", contextsText)
                .replace("{question}", question);

            ChatClient chatClient = chatClientBuilder.build();
            String answer = chatClient.prompt(prompt).call().content();

            log.info("答案生成完成，长度: {}", answer.length());
            return answer;
        } catch (Exception e) {
            log.error("答案生成失败", e);
            return "抱歉，生成答案时出现错误。";
        }
    }
}