package com.hqc.smrag.controller;

import com.hqc.smrag.common.GlobalExceptionHandler;
import com.hqc.smrag.service.DocPermissionService;
import com.hqc.smrag.service.knowledge.DocumentFileService;
import com.hqc.smrag.service.knowledge.DocumentService;
import com.hqc.smrag.service.knowledge.KnowledgeBaseService;
import com.hqc.smrag.service.knowledge.PermissionDecisionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class KnowledgeUploadControllerTest {

    private final DocumentService documentService = mock(DocumentService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        KnowledgeController controller = new KnowledgeController(
            documentService,
            mock(DocPermissionService.class),
            mock(DocumentFileService.class),
            mock(KnowledgeBaseService.class),
            mock(PermissionDecisionService.class)
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    @Test
    void passesExplicitChunkingStrategyToUploadService() throws Exception {
        when(documentService.uploadDocument(
            any(), eq("research"), eq(List.of("USER")), eq("RECURSIVE")
        )).thenReturn(42L);

        mockMvc.perform(multipart("/api/knowledge/upload")
                .file(file())
                .param("knowledgeBaseCode", "research")
                .param("roles", "USER")
                .param("chunkingStrategy", "RECURSIVE"))
            .andExpect(status().isOk());

        verify(documentService).uploadDocument(
            any(), eq("research"), eq(List.of("USER")), eq("RECURSIVE")
        );
    }

    @Test
    void keepsChunkingStrategyOptionalForOlderClients() throws Exception {
        when(documentService.uploadDocument(
            any(), eq("research"), eq(List.of("USER")), isNull()
        )).thenReturn(42L);

        mockMvc.perform(multipart("/api/knowledge/upload")
                .file(file())
                .param("knowledgeBaseCode", "research")
                .param("roles", "USER"))
            .andExpect(status().isOk());

        verify(documentService).uploadDocument(
            any(), eq("research"), eq(List.of("USER")), isNull()
        );
    }

    @Test
    void mapsInvalidStrategyToBadRequest() throws Exception {
        when(documentService.uploadDocument(
            any(), eq("research"), eq(List.of("USER")), eq("CHARACTERS")
        )).thenThrow(new IllegalArgumentException(
            "切块策略不合法，仅支持 TOKEN、RECURSIVE"
        ));

        mockMvc.perform(multipart("/api/knowledge/upload")
                .file(file())
                .param("knowledgeBaseCode", "research")
                .param("roles", "USER")
                .param("chunkingStrategy", "CHARACTERS"))
            .andExpect(status().isBadRequest());
    }

    private MockMultipartFile file() {
        return new MockMultipartFile(
            "file",
            "document.pdf",
            "application/pdf",
            "content".getBytes(StandardCharsets.UTF_8)
        );
    }
}
