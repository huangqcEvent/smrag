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

@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentParseRecoverer implements ConsumerRecordRecoverer {

    private static final String DEAD_LETTER_TOPIC = "document-parse.DLT";

    private final DocumentIngestionPersistenceService persistenceService;
    private final KafkaTemplate<String, String> kafkaTemplate;

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
