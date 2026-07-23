package com.hqc.smrag.service.knowledge;

import com.hqc.smrag.entity.KnowledgeDocument;
import com.hqc.smrag.mapper.KnowledgeDocumentMapper;
import com.hqc.smrag.service.storage.DocumentStorage;
import com.hqc.smrag.service.storage.StoredObject;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
@RequiredArgsConstructor
public class DocumentFileService {

    private final KnowledgeDocumentMapper documentMapper;
    private final PermissionDecisionService permissionDecisionService;
    private final DocumentStorage storage;

    public DocumentFile read(Long documentId, Long userId) {
        KnowledgeDocument document = documentMapper.selectById(documentId);
        if (document == null) {
            throw new IllegalArgumentException("文档不存在");
        }
        permissionDecisionService.requireDocumentRead(userId, documentId);
        try {
            if ("MINIO".equalsIgnoreCase(document.getStorageType())) {
                try (StoredObject stored = storage.get(document.getObjectKey(), null, null)) {
                    return new DocumentFile(
                        document.getName(),
                        contentType(document, stored.contentType()),
                        stored.stream().readAllBytes());
                }
            }
            Path path = Path.of(document.getFilePath());
            return new DocumentFile(
                document.getName(), contentType(document, Files.probeContentType(path)),
                Files.readAllBytes(path));
        } catch (IOException failure) {
            throw new IllegalStateException("读取文档失败", failure);
        }
    }

    private String contentType(KnowledgeDocument document, String detected) {
        if (detected != null && !detected.isBlank()) {
            return detected;
        }
        return switch (String.valueOf(document.getFileType()).toUpperCase()) {
            case "PDF" -> "application/pdf";
            case "TXT" -> "text/plain;charset=UTF-8";
            case "WORD" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            default -> "application/octet-stream";
        };
    }

    public record DocumentFile(String fileName, String contentType, byte[] content) {
    }
}
