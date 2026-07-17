package com.hqc.smrag.kafka;

import com.hqc.smrag.entity.DocumentIngestionOutbox;
import com.hqc.smrag.mapper.DocumentIngestionOutboxMapper;
import com.hqc.smrag.service.knowledge.DocumentIngestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * 文档解析 Outbox 发布器
 * <p>
 * 负责从 Outbox 表中读取待处理的文档解析任务，并发送到 Kafka 消息队列。
 * <p>
 * 工作流程：
 * <ol>
 *     <li>定时从 document_ingestion_outbox 表中锁定一批待处理记录</li>
 *     <li>将任务载荷发送到 Kafka topic "document-parse"</li>
 *     <li>更新 Outbox 记录状态为 PUBLISHED</li>
 *     <li>如果发送失败，更新尝试次数和下次尝试时间</li>
 * </ol>
 * <p>
 * 重试策略：采用指数退避，最大延迟 300 秒（5 分钟）。
 * <p>
 * 定时任务间隔：由配置 smrag.ai.rag.outbox-poll-delay-ms 控制，默认 1 秒。
 *
 * @see DocumentIngestionOutbox Outbox 实体
 * @see DocumentParseConsumer Kafka 消费者
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentOutboxPublisher {

    private static final String PARSE_TOPIC = "document-parse";
    private static final int BATCH_SIZE = 20;

    private final DocumentIngestionOutboxMapper outboxMapper;
    private final KafkaTemplate<String, String> kafkaTemplate;

    /**
     * 定时发布待处理的 Outbox 任务
     * <p>
     * 执行流程：
     * <ol>
     *     <li>从数据库中锁定最多 20 条待处理记录</li>
     *     <li>将每条记录的载荷发送到 Kafka</li>
     *     <li>成功发送后更新状态为 PUBLISHED</li>
     *     <li>失败则更新尝试次数和下次尝试时间</li>
     * </ol>
     */
    @Scheduled(fixedDelayString = "${smrag.ai.rag.outbox-poll-delay-ms:1000}")
    @Transactional
    public void publishPending() {
        for (DocumentIngestionOutbox outbox : outboxMapper.lockPendingBatch(BATCH_SIZE)) {
            try {
                kafkaTemplate.send(
                    PARSE_TOPIC,
                    outbox.getDocumentId().toString(),
                    outbox.getPayload()
                ).get(10, TimeUnit.SECONDS);
                outbox.setStatus("PUBLISHED");
                outbox.setPublishedAt(LocalDateTime.now());
                outbox.setLastError(null);
            } catch (Exception failure) {
                if (failure instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                int attempts = outbox.getAttemptCount() == null ? 1 : outbox.getAttemptCount() + 1;
                outbox.setAttemptCount(attempts);
                outbox.setNextAttemptAt(LocalDateTime.now().plusSeconds(Math.min(300, 1L << Math.min(attempts, 8))));
                outbox.setLastError(DocumentIngestionService.failureReason(failure));
                log.warn("Unable to publish document outbox event {}", outbox.getId(), failure);
            }
            outboxMapper.updateById(outbox);
        }
    }
}
