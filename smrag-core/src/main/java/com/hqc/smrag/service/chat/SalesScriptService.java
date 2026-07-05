package com.hqc.smrag.service.chat;

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
public class SalesScriptService {

    private final ChatClient chatClient;

    @Value("classpath:prompts/sales_script_prompt.txt")
    private Resource salesScriptPromptResource;

    public String generate(String productInfo, String customerInfo) {
        try {
            String promptTemplate = salesScriptPromptResource.getContentAsString(StandardCharsets.UTF_8);
            String prompt = promptTemplate
                .replace("{productInfo}", productInfo)
                .replace("{customerInfo}", customerInfo);

            String script = chatClient.prompt().user(prompt).call().content();

            log.info("销售话术生成完成，长度: {}", script.length());
            return script;
        } catch (Exception e) {
            log.error("销售话术生成失败", e);
            return "抱歉，生成销售话术时出现错误。";
        }
    }
}