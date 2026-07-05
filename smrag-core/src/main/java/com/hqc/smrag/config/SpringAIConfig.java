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
        return ChatClient.builder()
            .defaultFunctions(
                "productQueryTool",
                "managerQueryTool",
                "productCompareTool",
                "productRecommendTool"
            );
    }

    @Bean
    public org.springframework.ai.model.function.FunctionCallback productQueryCallback() {
        return org.springframework.ai.model.function.FunctionCallback.builder()
            .function("queryProduct", productQueryTool)
            .description("查询私募产品信息，根据产品名称返回产品详情")
            .build();
    }

    @Bean
    public org.springframework.ai.model.function.FunctionCallback managerQueryCallback() {
        return org.springframework.ai.model.function.FunctionCallback.builder()
            .function("queryManager", managerQueryTool)
            .description("查询私募管理人信息，根据管理人名称返回管理人详情")
            .build();
    }

    @Bean
    public org.springframework.ai.model.function.FunctionCallback productCompareCallback() {
        return org.springframework.ai.model.function.FunctionCallback.builder()
            .function("compareProducts", productCompareTool)
            .description("对比多个私募产品，输入产品名称列表（逗号分隔），返回对比结果")
            .build();
    }

    @Bean
    public org.springframework.ai.model.function.FunctionCallback productRecommendCallback() {
        return org.springframework.ai.model.function.FunctionCallback.builder()
            .function("recommendProducts", productRecommendTool)
            .description("推荐私募产品，输入风险等级和最大金额（格式：风险等级,金额），返回推荐产品列表")
            .build();
    }
}