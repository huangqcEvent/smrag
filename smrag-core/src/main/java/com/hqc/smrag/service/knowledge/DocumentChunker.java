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
