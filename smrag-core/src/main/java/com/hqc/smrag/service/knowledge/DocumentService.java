package com.hqc.smrag.service.knowledge;

import com.hqc.smrag.dto.DocumentParseTaskDTO;
import com.hqc.smrag.entity.KnowledgeDocument;
import com.hqc.smrag.enums.DocumentStatus;
import com.hqc.smrag.mapper.KnowledgeDocumentMapper;
import cn.hutool.core.io.FileUtil;
import cn.hutool.json.JSONUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    private final KnowledgeDocumentMapper documentMapper;
    private final KafkaTemplate<String, String> kafkaTemplate;

    private static final String UPLOAD_DIR = "uploads/";
    private static final String PARSE_TOPIC = "document-parse";

    public Long uploadDocument(MultipartFile file) throws IOException {
        // 保存文件
        String fileName = file.getOriginalFilename();
        String filePath = UPLOAD_DIR + System.currentTimeMillis() + "_" + fileName;
        File dest = new File(filePath);
        FileUtil.mkParentDirs(dest);
        file.transferTo(dest);

        // 创建文档记录
        KnowledgeDocument document = new KnowledgeDocument();
        document.setName(fileName);
        document.setFilePath(filePath);
        document.setFileType(getFileType(fileName));
        document.setStatus(DocumentStatus.PENDING.name());
        document.setCreateTime(LocalDateTime.now());
        documentMapper.insert(document);

        // 发送Kafka消息
        DocumentParseTaskDTO task = new DocumentParseTaskDTO(
            document.getId(), filePath, document.getFileType()
        );
        kafkaTemplate.send(PARSE_TOPIC, JSONUtil.toJsonStr(task));
        log.info("发送文档解析任务: {}", task);

        return document.getId();
    }

    public List<KnowledgeDocument> listDocuments() {
        return documentMapper.selectList(null);
    }

    public void deleteDocument(Long id) {
        KnowledgeDocument doc = documentMapper.selectById(id);
        if (doc != null) {
            FileUtil.del(doc.getFilePath());
            documentMapper.deleteById(id);
        }
    }

    private String getFileType(String fileName) {
        if (fileName.endsWith(".pdf")) return "PDF";
        if (fileName.endsWith(".doc") || fileName.endsWith(".docx")) return "WORD";
        if (fileName.endsWith(".txt")) return "TXT";
        return "UNKNOWN";
    }
}