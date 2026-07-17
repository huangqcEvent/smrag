package com.hqc.smrag.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChatRequestDTO {
    @NotBlank
    @Size(max = 128)
    private String sessionId;

    @NotBlank
    @Size(max = 4000)
    private String question;

    @Positive
    private Long userId;

    @Size(max = 128)
    private String knowledgeBaseId;

    @Positive
    private Long documentId;
}
