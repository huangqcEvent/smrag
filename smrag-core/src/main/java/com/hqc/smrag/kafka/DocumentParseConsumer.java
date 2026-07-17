package com.hqc.smrag.kafka;

import cn.hutool.json.JSONUtil;
import com.hqc.smrag.dto.DocumentParseTaskDTO;
import com.hqc.smrag.service.knowledge.DocumentIngestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentParseConsumer {

    private final DocumentIngestionService ingestionService;

    @KafkaListener(topics = "document-parse", groupId = "smrag-parse-group")
    public void consume(String message) {
        log.info("Received document ingestion task");
        DocumentParseTaskDTO task = JSONUtil.toBean(message, DocumentParseTaskDTO.class);
        ingestionService.process(task);
    }
}
