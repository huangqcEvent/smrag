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

@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentOutboxPublisher {

    private static final String PARSE_TOPIC = "document-parse";
    private static final int BATCH_SIZE = 20;

    private final DocumentIngestionOutboxMapper outboxMapper;
    private final KafkaTemplate<String, String> kafkaTemplate;

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
