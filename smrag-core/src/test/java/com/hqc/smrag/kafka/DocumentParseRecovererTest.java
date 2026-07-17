package com.hqc.smrag.kafka;

import cn.hutool.json.JSONUtil;
import com.hqc.smrag.dto.DocumentParseTaskDTO;
import com.hqc.smrag.service.knowledge.DocumentIngestionPersistenceService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.concurrent.CompletableFuture;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentParseRecovererTest {

    @Test
    @SuppressWarnings("unchecked")
    void marksMatchingVersionFailedAndPublishesDeadLetter() {
        DocumentIngestionPersistenceService persistenceService =
            mock(DocumentIngestionPersistenceService.class);
        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        DocumentParseRecoverer recoverer = new DocumentParseRecoverer(persistenceService, kafkaTemplate);
        DocumentParseTaskDTO task = new DocumentParseTaskDTO(
            42L,
            "uploads/enterprise-rag.pdf",
            "PDF",
            "default",
            2,
            "checksum-42",
            UUID.fromString("00000000-0000-0000-0000-000000000042")
        );
        String payload = JSONUtil.toJsonStr(task);
        ConsumerRecord<String, String> record =
            new ConsumerRecord<>("document-parse", 0, 12L, "42", payload);
        when(kafkaTemplate.send("document-parse.DLT", payload))
            .thenReturn(CompletableFuture.completedFuture(null));

        recoverer.accept(record, new IllegalStateException("permanent failure"));

        verify(persistenceService).markFailedInNewTransaction(
            eq(42L),
            eq(2),
            eq(UUID.fromString("00000000-0000-0000-0000-000000000042")),
            contains("permanent failure")
        );
        verify(kafkaTemplate).send("document-parse.DLT", payload);
    }

    @Test
    @SuppressWarnings("unchecked")
    void propagatesDeadLetterPublishFailure() {
        DocumentIngestionPersistenceService persistenceService =
            mock(DocumentIngestionPersistenceService.class);
        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        DocumentParseRecoverer recoverer = new DocumentParseRecoverer(persistenceService, kafkaTemplate);
        String payload = JSONUtil.toJsonStr(new DocumentParseTaskDTO(
            42L,
            "uploads/enterprise-rag.pdf",
            "PDF",
            "default",
            2,
            "checksum-42",
            UUID.fromString("00000000-0000-0000-0000-000000000042")
        ));
        ConsumerRecord<String, String> record =
            new ConsumerRecord<>("document-parse", 0, 12L, "42", payload);
        CompletableFuture failedSend = new CompletableFuture();
        failedSend.completeExceptionally(new IllegalStateException("broker unavailable"));
        when(kafkaTemplate.send("document-parse.DLT", payload)).thenReturn(failedSend);

        assertThatThrownBy(() -> recoverer.accept(record, new IllegalStateException("ingestion failed")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("dead-letter");
    }
}
