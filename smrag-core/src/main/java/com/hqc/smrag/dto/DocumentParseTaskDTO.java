package com.hqc.smrag.dto;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DocumentParseTaskDTO {
    private Long documentId;
    private String filePath;
    private String fileType;
}