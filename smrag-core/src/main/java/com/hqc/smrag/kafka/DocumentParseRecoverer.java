package com.hqc.smrag.kafka;

import cn.hutool.json.JSONUtil;
import com.hqc.smrag.dto.DocumentParseTaskDTO;
import com.hqc.smrag.service.knowledge.DocumentIngestionPersistenceService;
import com.hqc.smrag.service.knowledge.DocumentIngestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 文档解析恢复器
 * <p>
 * 实现 Spring Kafka 的 {@link ConsumerRecordRecoverer} 接口，
 * 用于处理重试失败的 Kafka 消息。
 * <p>
 * 恢复流程：
 * <ol>
 *     <li>解析消息载荷，获取文档ID和版本信息</li>
 *     <li>更新文档状态为 FAILED，记录失败原因</li>
 *     <li>将消息发送到死信队列（DLT）进行持久化</li>
 * </ol>
 * <p>
 * 死信队列：document-parse.DLT
 * <p>
 * 注意：失败状态的更新使用 REQUIRES_NEW 事务传播，确保即使外层事务回滚，状态也能保存。
 *
 * @see DocumentParseConsumer Kafka 消费者
 * @see DocumentIngestionPersistenceService#markFailedInNewTransaction 标记失败方法
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentParseRecoverer implements ConsumerRecordRecoverer {

    /**
     * 死信队列 topic 名称
     */
    private static final String DEAD_LETTER_TOPIC = "document-parse.DLT";

    private final DocumentIngestionPersistenceService persistenceService;
    private final KafkaTemplate<String, String> kafkaTemplate;

    /**
     * 恢复失败的消息
     * <p>
     * 处理步骤：
     * <ol>
     *     <li>从消息中提取文档解析任务</li>
     *     <li>更新文档状态为 FAILED</li>
     *     <li>将消息发送到死信队列</li>
     * </ol>
     *
     * @param record  Kafka 消息记录
     * @param failure 处理失败的异常
     */
    @Override
    public void accept(ConsumerRecord<?, ?> record, Exception failure) {
        String payload = String.valueOf(record.value());
        try {
            DocumentParseTaskDTO task = JSONUtil.toBean(payload, DocumentParseTaskDTO.class);
            if (task.getDocumentId() != null && task.getVersion() != null) {
                persistenceService.markFailedInNewTransaction(
                    task.getDocumentId(),
                    task.getVersion(),
                    task.getProcessingToken(),
                    DocumentIngestionService.failureReason(failure)
                );
            }
        } catch (RuntimeException statusFailure) {
            failure.addSuppressed(statusFailure);
            log.error("Unable to persist exhausted ingestion failure", statusFailure);
        }

        publishDeadLetter(payload, failure);
        log.error(
            "Document ingestion exhausted retries at offset {} and was sent to {}",
            record.offset(),
            DEAD_LETTER_TOPIC,
            failure
        );
    }

    /**
     * 发布死信消息
     * <p>
     * 将失败的消息发送到死信队列，等待人工干预或后续处理。
     *
     * @param payload          消息载荷
     * @param ingestionFailure 处理失败的异常
     */
    private void publishDeadLetter(String payload, Exception ingestionFailure) {
        try {
            kafkaTemplate.send(DEAD_LETTER_TOPIC, payload).get(10, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw deadLetterFailure(ingestionFailure, exception);
        } catch (ExecutionException | TimeoutException | RuntimeException exception) {
            throw deadLetterFailure(ingestionFailure, exception);
        }
    }

    /**
     * 构造死信发布失败异常
     */
    private IllegalStateException deadLetterFailure(
        Exception ingestionFailure,
        Exception publishFailure
    ) {
        IllegalStateException failure = new IllegalStateException(
            "Failed to publish document dead-letter record",
            publishFailure
        );
        failure.addSuppressed(ingestionFailure);
        return failure;
    }
}
