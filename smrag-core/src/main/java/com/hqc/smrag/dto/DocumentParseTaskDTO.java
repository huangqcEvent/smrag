package com.hqc.smrag.dto;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DocumentParseTaskDTO {
    private Long documentId;
    private String filePath;
    private String fileType;
    private String knowledgeBaseId;
    private Integer version;
    private String checksum;
    private UUID processingToken;

    public DocumentParseTaskDTO(Long documentId, String filePath, String fileType) {
        this(documentId, filePath, fileType, "default", 1, null, null);
    }
}
