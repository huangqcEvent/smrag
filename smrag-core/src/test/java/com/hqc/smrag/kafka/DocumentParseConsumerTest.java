package com.hqc.smrag.kafka;

import cn.hutool.json.JSONUtil;
import com.hqc.smrag.dto.DocumentParseTaskDTO;
import com.hqc.smrag.service.knowledge.DocumentIngestionService;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class DocumentParseConsumerTest {

    private final DocumentIngestionService ingestionService = mock(DocumentIngestionService.class);
    private final DocumentParseConsumer consumer = new DocumentParseConsumer(ingestionService);
    private final DocumentParseTaskDTO task = new DocumentParseTaskDTO(
        42L,
        "uploads/enterprise-rag.pdf",
        "PDF",
        1L,
        2,
        "checksum-42",
        UUID.fromString("00000000-0000-0000-0000-000000000042")
    );

    @Test
    void delegatesParsedTask() {
        consumer.consume(JSONUtil.toJsonStr(task));

        verify(ingestionService).process(task);
    }

    @Test
    void propagatesFailureSoKafkaCanRetry() {
        IllegalStateException failure = new IllegalStateException("ingestion failed");
        doThrow(failure).when(ingestionService).process(task);

        assertThatThrownBy(() -> consumer.consume(JSONUtil.toJsonStr(task))).isSameAs(failure);
    }
}
