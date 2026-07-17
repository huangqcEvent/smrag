package com.hqc.smrag.service.knowledge;

import cn.hutool.core.io.FileUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hqc.smrag.config.RagProperties;
import com.hqc.smrag.entity.KnowledgeChunk;
import com.hqc.smrag.entity.KnowledgeDocument;
import com.hqc.smrag.enums.DocumentStatus;
import com.hqc.smrag.mapper.KnowledgeChunkMapper;
import com.hqc.smrag.mapper.KnowledgeDocumentMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.DigestInputStream;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Service
public class DocumentService {

    private static final String DEFAULT_UPLOAD_DIR =
        System.getProperty("user.dir") + File.separator + "uploads";
    private static final String DEFAULT_KNOWLEDGE_BASE = "default";
    private final KnowledgeDocumentMapper documentMapper;
    private final KnowledgeChunkMapper chunkMapper;
    private final DocumentUploadPersistenceService uploadPersistenceService;
    private final VectorStore vectorStore;
    private final RagProperties properties;
    private final String uploadDir;

    @Autowired
    public DocumentService(
        KnowledgeDocumentMapper documentMapper,
        KnowledgeChunkMapper chunkMapper,
        DocumentUploadPersistenceService uploadPersistenceService,
        VectorStore vectorStore,
        RagProperties properties
    ) {
        this(
            documentMapper,
            chunkMapper,
            uploadPersistenceService,
            vectorStore,
            properties,
            DEFAULT_UPLOAD_DIR
        );
    }

    DocumentService(
        KnowledgeDocumentMapper documentMapper,
        KnowledgeChunkMapper chunkMapper,
        DocumentUploadPersistenceService uploadPersistenceService,
        VectorStore vectorStore,
        RagProperties properties,
        String uploadDir
    ) {
        this.documentMapper = documentMapper;
        this.chunkMapper = chunkMapper;
        this.uploadPersistenceService = uploadPersistenceService;
        this.vectorStore = vectorStore;
        this.properties = properties;
        this.uploadDir = uploadDir;
    }

    public Long uploadDocument(MultipartFile file) throws IOException {
        return uploadDocument(file, DEFAULT_KNOWLEDGE_BASE);
    }

    public Long uploadDocument(MultipartFile file, String knowledgeBaseId) throws IOException {
        validateFile(file);
        String normalizedKnowledgeBaseId = normalizeKnowledgeBaseId(knowledgeBaseId);
        String fileName = sanitizeFileName(file.getOriginalFilename());
        String fileType = getFileType(fileName);
        if ("UNKNOWN".equals(fileType)) {
            throw new IllegalArgumentException("不支持的文件类型，仅支持 PDF、DOC、DOCX、TXT");
        }

        String storedFileName = System.currentTimeMillis() + "_" + UUID.randomUUID() + "_" + fileName;
        Path destination = Path.of(uploadDir, storedFileName);
        Files.createDirectories(destination.getParent());
        String checksum;
        try {
            checksum = copyAndChecksum(file, destination);
        } catch (IOException | RuntimeException failure) {
            FileUtil.del(destination.toFile());
            throw failure;
        }

        KnowledgeDocument document = new KnowledgeDocument();
        document.setName(fileName);
        document.setFilePath(destination.toString());
        document.setFileType(fileType);
        document.setStatus(DocumentStatus.PENDING.name());
        document.setChunkCount(0);
        document.setKnowledgeBaseId(normalizedKnowledgeBaseId);
        document.setChecksum(checksum);
        document.setVersion(1);
        document.setProcessingToken(UUID.randomUUID());
        document.setCreateTime(LocalDateTime.now());
        document.setUpdateTime(LocalDateTime.now());

        try {
            Long documentId = uploadPersistenceService.createDocumentAndOutbox(document);
            log.info("Submitted document {} version {} for ingestion", document.getId(), document.getVersion());
            return documentId;
        } catch (RuntimeException failure) {
            FileUtil.del(destination.toFile());
            throw failure;
        }
    }

    public List<KnowledgeDocument> listDocuments() {
        return listDocuments(DEFAULT_KNOWLEDGE_BASE);
    }

    public List<KnowledgeDocument> listDocuments(String knowledgeBaseId) {
        String normalized = normalizeKnowledgeBaseId(knowledgeBaseId);
        return documentMapper.selectList(
            Wrappers.<KnowledgeDocument>lambdaQuery()
                .eq(KnowledgeDocument::getKnowledgeBaseId, normalized)
        );
    }

    public void deleteDocument(Long id) {
        deleteDocument(id, DEFAULT_KNOWLEDGE_BASE);
    }

    @Transactional
    public void deleteDocument(Long id, String knowledgeBaseId) {
        String normalized = normalizeKnowledgeBaseId(knowledgeBaseId);
        KnowledgeDocument document = documentMapper.selectById(id);
        if (document == null || !normalized.equals(document.getKnowledgeBaseId())) {
            return;
        }

        int claimed = documentMapper.update(
            null,
            Wrappers.<KnowledgeDocument>lambdaUpdate()
                .eq(KnowledgeDocument::getId, id)
                .ne(KnowledgeDocument::getStatus, DocumentStatus.DELETING.name())
                .set(KnowledgeDocument::getStatus, DocumentStatus.DELETING.name())
                .set(KnowledgeDocument::getUpdateTime, LocalDateTime.now())
        );
        if (claimed != 1) {
            return;
        }

        List<KnowledgeChunk> chunks = chunkMapper.selectList(
            Wrappers.<KnowledgeChunk>lambdaQuery().eq(KnowledgeChunk::getDocumentId, id)
        );
        List<String> vectorIds = chunks.stream()
            .map(KnowledgeChunk::getVectorId)
            .filter(java.util.Objects::nonNull)
            .map(UUID::toString)
            .toList();
        if (!vectorIds.isEmpty()) {
            vectorStore.delete(vectorIds);
        }

        chunkMapper.delete(
            Wrappers.<KnowledgeChunk>lambdaQuery().eq(KnowledgeChunk::getDocumentId, id)
        );
        documentMapper.deleteById(id);
        deleteFileAfterCommit(document.getFilePath());
    }

    private String copyAndChecksum(MultipartFile file, Path destination) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
        try (InputStream input = file.getInputStream();
             DigestInputStream digestInput = new DigestInputStream(input, digest)) {
            Files.copy(digestInput, destination, StandardCopyOption.REPLACE_EXISTING);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private void deleteFileAfterCommit(String filePath) {
        Runnable delete = () -> {
            if (filePath != null && !FileUtil.del(filePath)) {
                log.warn("Unable to delete document file {}", filePath);
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    delete.run();
                }
            });
        } else {
            delete.run();
        }
    }

    private String getFileType(String fileName) {
        String lowerFileName = fileName.toLowerCase(Locale.ROOT);
        if (lowerFileName.endsWith(".pdf")) return "PDF";
        if (lowerFileName.endsWith(".doc") || lowerFileName.endsWith(".docx")) return "WORD";
        if (lowerFileName.endsWith(".txt")) return "TXT";
        return "UNKNOWN";
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("文件不能为空");
        }
        if (file.getOriginalFilename() == null || file.getOriginalFilename().trim().isEmpty()) {
            throw new IllegalArgumentException("文件名不能为空");
        }
        if (file.getSize() > properties.getMaxUploadSizeBytes()) {
            throw new IllegalArgumentException("文件大小超过允许上限");
        }
    }

    private String sanitizeFileName(String originalFilename) {
        String normalized = originalFilename.replace('\\', '/');
        String fileName = normalized.substring(normalized.lastIndexOf('/') + 1).trim();
        if (fileName.isEmpty() || ".".equals(fileName) || "..".equals(fileName)) {
            throw new IllegalArgumentException("文件名不合法");
        }
        return fileName;
    }

    private String normalizeKnowledgeBaseId(String knowledgeBaseId) {
        String normalized = knowledgeBaseId == null || knowledgeBaseId.isBlank()
            ? DEFAULT_KNOWLEDGE_BASE
            : knowledgeBaseId.trim();
        if (!normalized.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")) {
            throw new IllegalArgumentException("知识库 ID 格式不合法");
        }
        return normalized;
    }
}
