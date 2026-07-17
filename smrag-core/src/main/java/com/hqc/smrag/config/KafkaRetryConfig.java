package com.hqc.smrag.config;

import com.hqc.smrag.kafka.DocumentParseRecoverer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;

/**
 * Kafka 重试配置类
 * <p>
 * 配置 Kafka 消息消费失败时的重试策略，采用指数退避算法。
 * 当文档解析消费失败时，会进行最多 2 次重试，重试间隔从 1 秒开始，每次翻倍。
 * <p>
 * 重试间隔序列：1s → 2s → 4s（最多10秒）
 *
 * @see DocumentParseRecoverer 重试失败后的恢复处理器
 */
@Configuration
public class KafkaRetryConfig {

    /**
     * 配置文档解析的错误处理器
     * <p>
     * 使用指数退避策略进行重试：
     * <ul>
     *     <li>最大重试次数：2 次</li>
     *     <li>初始间隔：1000ms（1秒）</li>
     *     <li>倍数：2.0（每次间隔翻倍）</li>
     *     <li>最大间隔：10000ms（10秒）</li>
     * </ul>
     * 重试失败后，会调用 {@link DocumentParseRecoverer} 进行恢复处理。
     *
     * @param recoverer 文档解析恢复处理器
     * @return 配置好的错误处理器
     */
    @Bean
    public CommonErrorHandler documentParseErrorHandler(DocumentParseRecoverer recoverer) {
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(2);
        backOff.setInitialInterval(1000L);
        backOff.setMultiplier(2.0);
        backOff.setMaxInterval(10000L);
        return new DefaultErrorHandler(recoverer, backOff);
    }
}
