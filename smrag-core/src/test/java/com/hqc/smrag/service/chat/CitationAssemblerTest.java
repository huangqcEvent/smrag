package com.hqc.smrag.service.chat;

import com.hqc.smrag.dto.CitationDTO;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CitationAssemblerTest {

    private final CitationAssembler assembler = new CitationAssembler();

    @Test
    void mapsDeduplicatesAndLimitsRetrievedDocuments() {
        String longText = "  Evidence   with repeated whitespace  " + "x".repeat(220);
        Document first = Document.builder()
            .id("vector-1")
            .text(longText)
            .score(0.91)
            .metadata(Map.of(
                "documentId", 42L,
                "knowledgeBaseId", 12L,
                "knowledgeBaseCode", "finance",
                "knowledgeBaseName", "财务制度库",
                "documentName", "policy.pdf",
                "chunkIndex", 3,
                "pageNumber", 7
            ))
            .build();
        Document duplicate = Document.builder()
            .id("vector-1")
            .text("duplicate")
            .metadata(Map.of())
            .build();
        Document second = Document.builder()
            .id("vector-2")
            .text("Second citation")
            .metadata(Map.of("documentId", "43", "pageNumber", "8"))
            .build();

        List<CitationDTO> citations = assembler.assemble(
            List.of(first, duplicate, second),
            2
        );

        assertThat(citations).hasSize(2);
        CitationDTO citation = citations.get(0);
        assertThat(citation.vectorId()).isEqualTo("vector-1");
        assertThat(citation.documentId()).isEqualTo(42L);
        assertThat(citation.knowledgeBaseId()).isEqualTo(12L);
        assertThat(citation.knowledgeBaseCode()).isEqualTo("finance");
        assertThat(citation.knowledgeBaseName()).isEqualTo("财务制度库");
        assertThat(citation.documentName()).isEqualTo("policy.pdf");
        assertThat(citation.chunkIndex()).isEqualTo(3);
        assertThat(citation.pageNumber()).isEqualTo(7);
        assertThat(citation.score()).isEqualTo(0.91);
        assertThat(citation.excerpt()).hasSize(180).doesNotContain("  ");
        assertThat(citations.get(1).documentId()).isEqualTo(43L);
        assertThat(citations.get(1).pageNumber()).isEqualTo(8);
    }
}
