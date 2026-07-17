package com.hqc.smrag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "smrag.ai.chat.stream")
public class ChatStreamProperties {

    private Duration timeout = Duration.ofSeconds(90);

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("chat stream timeout must be positive");
        }
        this.timeout = timeout;
    }
}
