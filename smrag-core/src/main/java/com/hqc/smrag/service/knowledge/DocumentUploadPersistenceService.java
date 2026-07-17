package com.hqc.smrag.service.knowledge;

import cn.hutool.json.JSONUtil;
import com.hqc.smrag.dto.DocumentParseTaskDTO;
import com.hqc.smrag.entity.DocumentIngestionOutbox;
import com.hqc.smrag.entity.KnowledgeDocument;
import com.hqc.smrag.mapper.DocumentIngestionOutboxMapper;
import com.hqc.smrag.mapper.KnowledgeDocumentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DocumentUploadPersistenceService {

    private final KnowledgeDocumentMapper documentMapper;
    private final DocumentIngestionOutboxMapper outboxMapper;

    @Transactional
    public Long createDocumentAndOutbox(KnowledgeDocument document) {
        documentMapper.insert(document);
        DocumentParseTaskDTO task = new DocumentParseTaskDTO(
            document.getId(),
            document.getFilePath(),
            document.getFileType(),
            document.getKnowledgeBaseId(),
            document.getVersion(),
            document.getChecksum(),
            document.getProcessingToken()
        );
        LocalDateTime now = LocalDateTime.now();
        DocumentIngestionOutbox outbox = new DocumentIngestionOutbox();
        outbox.setId(UUID.randomUUID());
        outbox.setDocumentId(document.getId());
        outbox.setPayload(JSONUtil.toJsonStr(task));
        outbox.setStatus("PENDING");
        outbox.setAttemptCount(0);
        outbox.setNextAttemptAt(now);
        outbox.setCreatedAt(now);
        outboxMapper.insert(outbox);
        return document.getId();
    }
}
