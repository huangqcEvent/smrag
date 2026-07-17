package com.hqc.smrag.kafka;

import com.hqc.smrag.entity.DocumentIngestionOutbox;
import com.hqc.smrag.mapper.DocumentIngestionOutboxMapper;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class DocumentOutboxPublisherTest {

    private final DocumentIngestionOutboxMapper outboxMapper =
        mock(DocumentIngestionOutboxMapper.class);
    private final KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
    private final DocumentOutboxPublisher publisher =
        new DocumentOutboxPublisher(outboxMapper, kafkaTemplate);

    @Test
    void marksEventPublishedAfterKafkaAcknowledgesIt() {
        DocumentIngestionOutbox outbox = pendingOutbox();
        when(outboxMapper.lockPendingBatch(20)).thenReturn(List.of(outbox));
        when(kafkaTemplate.send("document-parse", "42", outbox.getPayload()))
            .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

        publisher.publishPending();

        assertThat(outbox.getStatus()).isEqualTo("PUBLISHED");
        assertThat(outbox.getPublishedAt()).isNotNull();
        assertThat(outbox.getLastError()).isNull();
        verify(outboxMapper).updateById(outbox);
    }

    @Test
    void schedulesRetryWhenKafkaPublishFails() {
        DocumentIngestionOutbox outbox = pendingOutbox();
        LocalDateTime previousAttemptAt = outbox.getNextAttemptAt();
        when(outboxMapper.lockPendingBatch(20)).thenReturn(List.of(outbox));
        when(kafkaTemplate.send("document-parse", "42", outbox.getPayload()))
            .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker unavailable")));

        publisher.publishPending();

        assertThat(outbox.getStatus()).isEqualTo("PENDING");
        assertThat(outbox.getAttemptCount()).isEqualTo(1);
        assertThat(outbox.getNextAttemptAt()).isAfter(previousAttemptAt);
        assertThat(outbox.getLastError()).contains("broker unavailable");
        verify(outboxMapper).updateById(outbox);
        verify(kafkaTemplate).send(eq("document-parse"), eq("42"), eq(outbox.getPayload()));
    }

    private DocumentIngestionOutbox pendingOutbox() {
        DocumentIngestionOutbox outbox = new DocumentIngestionOutbox();
        outbox.setId(UUID.fromString("00000000-0000-0000-0000-000000000042"));
        outbox.setDocumentId(42L);
        outbox.setPayload("{\"documentId\":42}");
        outbox.setStatus("PENDING");
        outbox.setAttemptCount(0);
        outbox.setNextAttemptAt(LocalDateTime.now());
        outbox.setCreatedAt(LocalDateTime.now());
        return outbox;
    }
}
