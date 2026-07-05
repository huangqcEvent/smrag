package com.hqc.smrag.dto;

import lombok.Data;

@Data
public class ChatRequestDTO {
    private String sessionId;
    private String question;
    private Long userId;
}