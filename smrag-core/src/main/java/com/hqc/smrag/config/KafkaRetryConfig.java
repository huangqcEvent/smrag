package com.hqc.smrag.config;

import com.hqc.smrag.kafka.DocumentParseRecoverer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;

@Configuration
public class KafkaRetryConfig {

    @Bean
    public CommonErrorHandler documentParseErrorHandler(DocumentParseRecoverer recoverer) {
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(2);
        backOff.setInitialInterval(1000L);
        backOff.setMultiplier(2.0);
        backOff.setMaxInterval(10000L);
        return new DefaultErrorHandler(recoverer, backOff);
    }
}
