package com.hqc.smrag.kafka;

import com.hqc.smrag.dto.DocumentParseTaskDTO;
import com.hqc.smrag.entity.KnowledgeDocument;
import com.hqc.smrag.entity.KnowledgeChunk;
import com.hqc.smrag.enums.DocumentStatus;
import com.hqc.smrag.mapper.KnowledgeDocumentMapper;
import com.hqc.smrag.mapper.KnowledgeChunkMapper;
import com.hqc.smrag.service.knowledge.VectorService;
import com.hqc.smrag.utils.TikaUtils;
import com.hqc.smrag.utils.ChunkUtils;
import cn.hutool.json.JSONUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentParseConsumer {

    private final KnowledgeDocumentMapper documentMapper;
    private final KnowledgeChunkMapper chunkMapper;
    private final VectorService vectorService;

    @KafkaListener(topics = "document-parse", groupId = "smrag-parse-group")
    public void consume(String message) {
        log.info("接收消息: {}", message);
        try {
            DocumentParseTaskDTO task = JSONUtil.toBean(message, DocumentParseTaskDTO.class);
            processDocument(task);
        } catch (Exception e) {
            log.error("解析消息失败", e);
        }
    }

    private void processDocument(DocumentParseTaskDTO task) throws IOException {
        // 更新状态为处理中
        KnowledgeDocument doc = documentMapper.selectById(task.getDocumentId());
        doc.setStatus(DocumentStatus.PROCESSING.name());
        doc.setUpdateTime(LocalDateTime.now());
        documentMapper.updateById(doc);

        try {
            // 提取文本
            String text = TikaUtils.extractText(task.getFilePath());

            // 切片
            List<String> chunks = ChunkUtils.chunkText(text);

            // 存储切片和向量
            for (int i = 0; i < chunks.size(); i++) {
                // 存储切片记录
                KnowledgeChunk chunk = new KnowledgeChunk();
                chunk.setDocumentId(task.getDocumentId());
                chunk.setContent(chunks.get(i));
                chunk.setChunkIndex(i);
                chunk.setCreateTime(LocalDateTime.now());
                chunkMapper.insert(chunk);

                // 存储向量
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("documentId", task.getDocumentId());
                metadata.put("chunkIndex", i);
                metadata.put("documentName", doc.getName());
                vectorService.storeVector(chunks.get(i), metadata);
            }

            // 更新状态为完成
            doc.setStatus(DocumentStatus.COMPLETED.name());
            doc.setChunkCount(chunks.size());
            doc.setUpdateTime(LocalDateTime.now());
            documentMapper.updateById(doc);
            log.info("文档解析完成，切片数: {}", chunks.size());

        } catch (Exception e) {
            doc.setStatus(DocumentStatus.FAILED.name());
            doc.setUpdateTime(LocalDateTime.now());
            documentMapper.updateById(doc);
            log.error("文档解析失败", e);
        }
    }
}