package com.hqc.smrag.service.knowledge;

import com.hqc.smrag.config.RagProperties;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 文档分块器
 * <p>
 * 将文档内容切分为适合向量化的块，并添加元数据。
 * 主要功能：
 * <ul>
 *     <li>文本分块：使用 TokenTextSplitter 按字符数切分</li>
 *     <li>元数据增强：添加知识库ID、文档ID、页码等元数据</li>
 *     <li>ID生成：为每个分块生成唯一的向量ID</li>
 * </ul>
 * <p>
 * 分块参数由 {@link RagProperties} 配置：
 * <ul>
 *     <li>chunkSize：分块大小</li>
 *     <li>minChunkSizeChars：最小分块大小</li>
 *     <li>minChunkLengthToEmbed：最小嵌入长度</li>
 *     <li>maxNumChunks：最大分块数量</li>
 *     <li>keepSeparator：是否保留分隔符</li>
 * </ul>
 *
 * @see TokenTextSplitter Spring AI 分块器
 * @see VectorDocumentIdGenerator 向量ID生成器
 */
@Component
public class DocumentChunker {

    private final TokenTextSplitter splitter;
    private final VectorDocumentIdGenerator idGenerator;

    public DocumentChunker(RagProperties properties, VectorDocumentIdGenerator idGenerator) {
        this.splitter = TokenTextSplitter.builder()
            .withChunkSize(properties.getChunkSize())
            .withMinChunkSizeChars(properties.getMinChunkSizeChars())
            .withMinChunkLengthToEmbed(properties.getMinChunkLengthToEmbed())
            .withMaxNumChunks(properties.getMaxNumChunks())
            .withKeepSeparator(properties.isKeepSeparator())
            .build();
        this.idGenerator = idGenerator;
    }

    /**
     * 将文档切分为多个块
     * <p>
     * 处理流程：
     * <ol>
     *     <li>为每个源文档添加元数据（知识库ID、文档ID等）</li>
     *     <li>使用 TokenTextSplitter 切分文档</li>
     *     <li>为每个分块生成唯一ID并添加分块索引</li>
     * </ol>
     *
     * @param sourceDocuments  源文档列表（PDF页或段落）
     * @param knowledgeBaseId  知识库ID
     * @param documentId       文档ID
     * @param documentName     文档名称
     * @param documentType     文档类型
     * @param documentVersion  文档版本
     * @param documentStatus   文档状态
     * @param checksum         文档校验和
     * @return 分块后的文档列表
     */
    public List<Document> chunk(
        List<Document> sourceDocuments,
        String knowledgeBaseId,
        long documentId,
        String documentName,
        String documentType,
        int documentVersion,
        String documentStatus,
        String checksum
    ) {
        List<Document> enrichedDocuments = sourceDocuments.stream()
            .map(source -> enrich(
                source,
                knowledgeBaseId,
                documentId,
                documentName,
                documentType,
                documentVersion,
                documentStatus,
                checksum
            ))
            .toList();
        List<Document> splitDocuments = splitter.apply(enrichedDocuments);
        List<Document> chunks = new ArrayList<>(splitDocuments.size());

        for (int chunkIndex = 0; chunkIndex < splitDocuments.size(); chunkIndex++) {
            Document chunk = splitDocuments.get(chunkIndex);
            Map<String, Object> metadata = new HashMap<>(chunk.getMetadata());
            metadata.put("chunkIndex", chunkIndex);
            UUID vectorId = idGenerator.generate(
                knowledgeBaseId,
                documentId,
                documentVersion,
                chunkIndex
            );
            chunks.add(new Document(vectorId.toString(), chunk.getText(), metadata));
        }
        return chunks;
    }

    /**
     * 为文档添加元数据
     * <p>
     * 添加的元数据包括：
     * <ul>
     *     <li>knowledgeBaseId：知识库ID</li>
     *     <li>documentId：文档ID</li>
     *     <li>documentName：文档名称</li>
     *     <li>documentType：文档类型</li>
     *     <li>documentVersion：文档版本</li>
     *     <li>documentStatus：文档状态</li>
     *     <li>checksum：校验和</li>
     *     <li>pageNumber：页码（从PDF阅读器元数据中提取）</li>
     * </ul>
     */
    private Document enrich(
        Document source,
        String knowledgeBaseId,
        long documentId,
        String documentName,
        String documentType,
        int documentVersion,
        String documentStatus,
        String checksum
    ) {
        Map<String, Object> metadata = new HashMap<>(source.getMetadata());
        Object pageNumber = metadata.remove(PagePdfDocumentReader.METADATA_START_PAGE_NUMBER);
        if (pageNumber != null) {
            metadata.put("pageNumber", pageNumber);
        }
        metadata.put("knowledgeBaseId", knowledgeBaseId);
        metadata.put("documentId", documentId);
        metadata.put("documentName", documentName);
        metadata.put("documentType", documentType);
        metadata.put("documentVersion", documentVersion);
        metadata.put("documentStatus", documentStatus);
        metadata.put("checksum", checksum);
        return new Document(source.getText(), metadata);
    }
}
