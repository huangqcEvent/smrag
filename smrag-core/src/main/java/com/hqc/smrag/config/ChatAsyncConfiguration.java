package com.hqc.smrag.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Duration;

@Configuration
@EnableConfigurationProperties(ChatStreamProperties.class)
public class ChatAsyncConfiguration implements WebMvcConfigurer {

    private final ChatStreamProperties streamProperties;

    public ChatAsyncConfiguration(ChatStreamProperties streamProperties) {
        this.streamProperties = streamProperties;
    }

    @Bean
    public ThreadPoolTaskExecutor chatMvcTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("chat-mvc-");
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(200);
        executor.setKeepAliveSeconds(30);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        return executor;
    }

    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        Duration servletTimeout = streamProperties.getTimeout().plusSeconds(15);
        configurer.setDefaultTimeout(servletTimeout.toMillis());
        configurer.setTaskExecutor(chatMvcTaskExecutor());
    }
}
