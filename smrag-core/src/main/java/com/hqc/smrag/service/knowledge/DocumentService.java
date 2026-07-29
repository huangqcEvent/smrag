package com.hqc.smrag.service.knowledge;

import cn.hutool.core.io.FileUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hqc.smrag.common.PageResult;
import com.hqc.smrag.config.RagProperties;
import com.hqc.smrag.config.MinioStorageProperties;
import com.hqc.smrag.entity.KnowledgeChunk;
import com.hqc.smrag.entity.KnowledgeDocument;
import com.hqc.smrag.enums.DocumentStatus;
import com.hqc.smrag.mapper.KnowledgeChunkMapper;
import com.hqc.smrag.mapper.KnowledgeDocumentMapper;
import com.hqc.smrag.service.DocPermissionService;
import com.hqc.smrag.service.storage.DocumentStorage;
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

/**
 * 文档服务
 * <p>
 * 提供知识库文档的管理能力，包括：
 * <ul>
 *     <li>上传文档：接收文件并保存到本地，发送到 Kafka 进行异步解析</li>
 *     <li>查询文档列表：查询指定知识库下的所有文档</li>
 *     <li>删除文档：删除文档及其相关的向量数据和分块记录</li>
 * </ul>
 * <p>
 * 支持的文件类型：PDF、Word（.doc/.docx）、TXT
 * <p>
 * 上传流程：
 * <ol>
 *     <li>校验文件（大小、类型、名称）</li>
 *     <li>计算文件 SHA-256 校验和</li>
 *     <li>保存文件到本地</li>
 *     <li>创建文档记录和 Outbox 记录（事务）</li>
 *     <li>Kafka 消费者异步处理文档解析</li>
 * </ol>
 *
 * @see DocumentUploadPersistenceService 文档上传持久化服务
 * @see DocumentIngestionService 文档解析服务
 */
@Slf4j
@Service
public class DocumentService {

    private static final String DEFAULT_UPLOAD_DIR =
        System.getProperty("user.dir") + File.separator + "uploads";
    private static final int DEFAULT_DOCUMENT_PAGE_SIZE = 20;
    private static final int MAX_DOCUMENT_PAGE_SIZE = 100;
    private final KnowledgeDocumentMapper documentMapper;
    private final KnowledgeChunkMapper chunkMapper;
    private final DocumentUploadPersistenceService uploadPersistenceService;
    private final VectorStore vectorStore;
    private final RagProperties properties;
    private final String uploadDir;
    private final DocPermissionService docPermissionService;
    private final DocumentStorage documentStorage;
    private final String objectPrefix;
    private final KnowledgeBaseService knowledgeBaseService;

    @Autowired
    public DocumentService(
        KnowledgeDocumentMapper documentMapper,
        KnowledgeChunkMapper chunkMapper,
        DocumentUploadPersistenceService uploadPersistenceService,
        VectorStore vectorStore,
        RagProperties properties,
        DocPermissionService docPermissionService,
        DocumentStorage documentStorage,
        KnowledgeBaseService knowledgeBaseService,
        MinioStorageProperties storageProperties
    ) {
        this(
            documentMapper,
            chunkMapper,
            uploadPersistenceService,
            vectorStore,
            properties,
            DEFAULT_UPLOAD_DIR,
            docPermissionService,
            documentStorage,
            storageProperties.getObjectPrefix(),
            knowledgeBaseService
        );
    }

    DocumentService(
        KnowledgeDocumentMapper documentMapper,
        KnowledgeChunkMapper chunkMapper,
        DocumentUploadPersistenceService uploadPersistenceService,
        VectorStore vectorStore,
        RagProperties properties,
        String uploadDir,
        DocPermissionService docPermissionService,
        DocumentStorage documentStorage,
        String objectPrefix,
        KnowledgeBaseService knowledgeBaseService
    ) {
        this.documentMapper = documentMapper;
        this.chunkMapper = chunkMapper;
        this.uploadPersistenceService = uploadPersistenceService;
        this.vectorStore = vectorStore;
        this.properties = properties;
        this.uploadDir = uploadDir;
        this.docPermissionService = docPermissionService;
        this.documentStorage = documentStorage;
        this.objectPrefix = objectPrefix;
        this.knowledgeBaseService = knowledgeBaseService;
    }

    /**
     * 上传文档到指定知识库
     * <p>
     * 处理流程：
     * <ol>
     *     <li>校验文件（大小、类型、名称）</li>
     *     <li>生成唯一文件名并保存到本地</li>
     *     <li>计算文件 SHA-256 校验和</li>
     *     <li>创建文档记录和 Outbox 记录</li>
     * </ol>
     *
     * @param file            上传的文件
     * @param knowledgeBaseId 知识库ID
     * @return 文档ID
     * @throws IOException              文件处理异常
     * @throws IllegalArgumentException 文件校验失败时抛出
     */
    public Long uploadDocument(
        MultipartFile file,
        String knowledgeBaseId,
        List<String> roleCodes
    ) throws IOException {
        validateFile(file);
        List<String> normalizedRoles = roleCodes == null ? List.of() : roleCodes.stream()
            .filter(java.util.Objects::nonNull)
            .map(String::trim)
            .filter(role -> !role.isEmpty())
            .map(role -> role.toUpperCase(Locale.ROOT))
            .distinct()
            .toList();
        if (normalizedRoles.isEmpty()) {
            throw new IllegalArgumentException("文档至少需要一个可读角色");
        }
        if (normalizedRoles.contains(
            PermissionDecisionService.GLOBAL_ADMIN_ROLE_CODE
        )) {
            throw new IllegalArgumentException(
                "ADMIN 是全局管理员角色，不能加入文档访问权限"
            );
        }
        Long normalizedKnowledgeBaseId =
            knowledgeBaseService.requireActive(knowledgeBaseId).getId();
        String fileName = sanitizeFileName(file.getOriginalFilename());
        String fileType = getFileType(fileName);
        if ("UNKNOWN".equals(fileType)) {
            throw new IllegalArgumentException("不支持的文件类型，仅支持 PDF、DOC、DOCX、TXT");
        }

        KnowledgeDocument document = new KnowledgeDocument();
        document.setName(fileName);
        document.setStorageType("MINIO");
        document.setFileType(fileType);
        document.setStatus(DocumentStatus.PENDING.name());
        document.setChunkCount(0);
        document.setKnowledgeBaseId(normalizedKnowledgeBaseId);
        document.setAclVersion(0);
        document.setVersion(1);
        document.setProcessingToken(UUID.randomUUID());
        document.setCreateTime(LocalDateTime.now());
        document.setUpdateTime(LocalDateTime.now());

        Long documentId = uploadPersistenceService.createPlaceholder(document);
        String objectKey = objectPrefix + "/" + documentId + "/"
            + UUID.randomUUID() + "-" + fileName;
        boolean objectStored = false;
        try {
            String checksum = checksum(file);
            try (InputStream input = file.getInputStream()) {
                documentStorage.put(
                    objectKey, input, file.getSize(), file.getContentType());
            }
            objectStored = true;
            uploadPersistenceService.completeUpload(
                documentId, objectKey, checksum, normalizedRoles);
            log.info("Submitted document {} version {} for ingestion", document.getId(), document.getVersion());
            return documentId;
        } catch (IOException | RuntimeException failure) {
            if (objectStored) {
                try {
                    documentStorage.delete(objectKey);
                } catch (RuntimeException cleanupFailure) {
                    failure.addSuppressed(cleanupFailure);
                }
            }
            try {
                uploadPersistenceService.deletePlaceholder(documentId);
            } catch (RuntimeException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }

    private String checksum(MultipartFile file) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
        try (InputStream input = file.getInputStream();
             DigestInputStream digestInput = new DigestInputStream(input, digest)) {
            digestInput.transferTo(java.io.OutputStream.nullOutputStream());
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    /**
     * 查询指定知识库的文档列表
     *
     * @param knowledgeBaseId 知识库ID
     * @return 文档列表
     */
    public PageResult<KnowledgeDocument> listDocuments(
        String knowledgeBaseId,
        Integer pageNum,
        Integer pageSize,
        String keyword,
        String status
    ) {
        Long normalized = knowledgeBaseService.requireByCode(knowledgeBaseId).getId();
        long current = normalizePageNum(pageNum);
        long size = normalizePageSize(pageSize);
        String normalizedKeyword = normalizeKeyword(keyword);
        String normalizedStatus = normalizeStatus(status);
        Page<KnowledgeDocument> page = documentMapper.selectPage(
            new Page<>(current, size),
            Wrappers.<KnowledgeDocument>lambdaQuery()
                .eq(KnowledgeDocument::getKnowledgeBaseId, normalized)
                .like(
                    normalizedKeyword != null,
                    KnowledgeDocument::getName,
                    normalizedKeyword
                )
                .eq(
                    normalizedStatus != null,
                    KnowledgeDocument::getStatus,
                    normalizedStatus
                )
                .orderByDesc(KnowledgeDocument::getCreateTime)
                .orderByDesc(KnowledgeDocument::getId)
        );
        return new PageResult<>(
            page.getRecords(),
            page.getTotal(),
            page.getCurrent(),
            page.getSize(),
            page.getCurrent() * page.getSize() < page.getTotal()
        );
    }

    private long normalizePageNum(Integer pageNum) {
        return pageNum == null || pageNum < 1 ? 1L : pageNum;
    }

    private long normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return DEFAULT_DOCUMENT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_DOCUMENT_PAGE_SIZE);
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null) return null;
        String normalized = keyword.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String normalizeStatus(String status) {
        if (status == null) return null;
        String normalized = status.trim();
        if (normalized.isEmpty()) return null;
        return normalized.toUpperCase(Locale.ROOT);
    }

    /**
     * 删除指定知识库中的文档
     * <p>
     * 删除流程：
     * <ol>
     *     <li>乐观锁标记文档为删除中状态</li>
     *     <li>删除向量数据库中的相关向量</li>
     *     <li>删除数据库中的分块记录</li>
     *     <li>删除文档记录</li>
     *     <li>事务提交后删除物理文件</li>
     * </ol>
     *
     * @param id              文档ID
     * @param knowledgeBaseId 知识库ID
     */
    @Transactional
    public void deleteDocument(Long id, String knowledgeBaseId) {
        Long normalized = knowledgeBaseService.requireByCode(knowledgeBaseId).getId();
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
        deleteFileAfterCommit(document);
    }

    /**
     * 复制文件并计算校验和
     * <p>
     * 使用 SHA-256 算法计算文件校验和，用于去重和完整性校验。
     *
     * @param file        上传的文件
     * @param destination 目标路径
     * @return SHA-256 校验和（十六进制字符串）
     * @throws IOException 文件处理异常
     */
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

    /**
     * 事务提交后删除文件
     * <p>
     * 如果当前存在活跃事务，则注册事务同步器，在事务提交后删除文件。
     * 否则直接删除文件。
     *
     * @param filePath 文件路径
     */
    private void deleteFileAfterCommit(KnowledgeDocument document) {
        Runnable delete = () -> {
            if ("MINIO".equalsIgnoreCase(document.getStorageType())
                && document.getObjectKey() != null) {
                documentStorage.delete(document.getObjectKey());
                if (document.getParsedTextObjectKey() != null) {
                    documentStorage.delete(document.getParsedTextObjectKey());
                }
            } else if (document.getFilePath() != null
                && !FileUtil.del(document.getFilePath())) {
                log.warn("Unable to delete document file {}", document.getFilePath());
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

    /**
     * 获取文件类型
     *
     * @param fileName 文件名
     * @return 文件类型（PDF、WORD、TXT、UNKNOWN）
     */
    private String getFileType(String fileName) {
        String lowerFileName = fileName.toLowerCase(Locale.ROOT);
        if (lowerFileName.endsWith(".pdf")) return "PDF";
        if (lowerFileName.endsWith(".doc") || lowerFileName.endsWith(".docx")) return "WORD";
        if (lowerFileName.endsWith(".txt")) return "TXT";
        return "UNKNOWN";
    }

    /**
     * 校验文件
     *
     * @param file 上传的文件
     * @throws IllegalArgumentException 校验失败时抛出
     */
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

    /**
     * 清理文件名
     * <p>
     * 去除路径部分，只保留文件名，并校验文件名合法性。
     *
     * @param originalFilename 原始文件名
     * @return 清理后的文件名
     * @throws IllegalArgumentException 文件名非法时抛出
     */
    private String sanitizeFileName(String originalFilename) {
        String normalized = originalFilename.replace('\\', '/');
        String fileName = normalized.substring(normalized.lastIndexOf('/') + 1).trim();
        if (fileName.isEmpty() || ".".equals(fileName) || "..".equals(fileName)) {
            throw new IllegalArgumentException("文件名不合法");
        }
        return fileName;
    }

}
