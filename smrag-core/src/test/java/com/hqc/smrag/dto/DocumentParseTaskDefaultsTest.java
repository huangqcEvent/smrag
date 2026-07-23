package com.hqc.smrag.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentParseTaskDefaultsTest {

    @Test
    void simplifiedTaskUsesCompanyMainKnowledgeBase() {
        DocumentParseTaskDTO task =
            new DocumentParseTaskDTO(42L, "document.pdf", "PDF");

        assertThat(task.getKnowledgeBaseId()).isEqualTo("company-main");
    }
}
