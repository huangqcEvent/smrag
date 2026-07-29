package com.hqc.smrag.service.knowledge;

import com.hqc.smrag.config.RagProperties;
import com.hqc.smrag.enums.ChunkingStrategy;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentChunkerTest {

    @Test
    void preservesBusinessMetadataAndAssignsStableVectorIds() {
        RagProperties properties = new RagProperties();
        properties.setChunkSize(30);
        properties.setMinChunkSizeChars(20);
        properties.setMinChunkLengthToEmbed(1);
        properties.setMaxNumChunks(20);

        VectorDocumentIdGenerator idGenerator = new VectorDocumentIdGenerator();
        DocumentChunker chunker = chunker(properties, idGenerator);
        List<Document> sources = List.of(
            new Document(
                "第一页包含足够长的企业知识库内容，用于验证中文文档会被切分并完整保留来源页码。"
                    + "第二句话继续补充检索增强生成系统的上下文。",
                Map.of(PagePdfDocumentReader.METADATA_START_PAGE_NUMBER, 7)
            ),
            new Document("第二页继续描述向量检索、重排和答案生成流程，并验证多个源文档的切片顺序。")
        );

        List<Document> chunks = chunker.chunk(
            sources,
            ChunkingStrategy.TOKEN,
            1L,
            42L,
            "enterprise-rag.pdf",
            "PDF",
            2,
            "PROCESSING",
            "checksum-42",
            3,
            Set.of(1L)
        );

        assertThat(chunks).isNotEmpty();
        assertThat(chunks).allSatisfy(chunk -> {
            assertThat(UUID.fromString(chunk.getId())).isNotNull();
            assertThat(chunk.getMetadata())
                .containsEntry("knowledgeBaseId", 1L)
                .containsEntry("documentId", 42L)
                .containsEntry("documentName", "enterprise-rag.pdf")
                .containsEntry("documentType", "PDF")
                .containsEntry("documentVersion", 2)
                .containsEntry("documentStatus", "PROCESSING")
                .containsEntry("checksum", "checksum-42")
                .containsEntry("documentAclVersion", 3)
                .doesNotContainKeys(
                    "aclVersion",
                    "aclDenied",
                    "aclRoleId_1",
                    "aclRole_1"
                )
                .containsKey("chunkIndex");
        });
        assertThat(chunks)
            .anySatisfy(chunk -> assertThat(chunk.getMetadata()).containsEntry("pageNumber", 7));

        List<Document> repeated = chunker.chunk(
            sources,
            ChunkingStrategy.TOKEN,
            1L,
            42L,
            "enterprise-rag.pdf",
            "PDF",
            2,
            "PROCESSING",
            "checksum-42",
            3,
            Set.of(1L)
        );
        assertThat(repeated).extracting(Document::getId)
            .containsExactlyElementsOf(chunks.stream().map(Document::getId).toList());
    }

    @Test
    void writesOnlyAclVersionRatherThanMutableRoleSnapshots() {
        RagProperties properties = new RagProperties();
        properties.setChunkSize(30);
        properties.setMinChunkSizeChars(1);
        properties.setMinChunkLengthToEmbed(1);
        DocumentChunker chunker =
            chunker(properties, new VectorDocumentIdGenerator());

        List<Document> chunks = chunker.chunk(
            List.of(new Document("permission aware knowledge content")),
            ChunkingStrategy.TOKEN,
            1L, 42L, "manual.txt", "TXT", 1, "COMPLETED",
            "checksum", 3, Set.of(1L, 5L));

        assertThat(chunks).isNotEmpty().allSatisfy(chunk ->
            assertThat(chunk.getMetadata())
                .containsEntry("documentAclVersion", 3)
                .doesNotContainKeys(
                    "aclVersion",
                    "aclDenied",
                    "aclRoleId_1",
                    "aclRoleId_5",
                    "aclRole_1",
                    "aclRole_5"
                ));
    }

    @Test
    void routesOnlyToSelectedStrategy() {
        DocumentSplittingStrategy token = mock(DocumentSplittingStrategy.class);
        DocumentSplittingStrategy recursive =
            mock(DocumentSplittingStrategy.class);
        when(token.type()).thenReturn(ChunkingStrategy.TOKEN);
        when(recursive.type()).thenReturn(ChunkingStrategy.RECURSIVE);
        when(recursive.split(anyList()))
            .thenReturn(List.of(new Document("recursive result")));
        DocumentChunker chunker = new DocumentChunker(
            List.of(token, recursive),
            new VectorDocumentIdGenerator()
        );

        List<Document> chunks = chunker.chunk(
            List.of(new Document("source")),
            ChunkingStrategy.RECURSIVE,
            1L, 42L, "manual.txt", "TXT", 1, "COMPLETED",
            "checksum", 3, Set.of(1L)
        );

        assertThat(chunks).extracting(Document::getText)
            .containsExactly("recursive result");
        verify(recursive).split(anyList());
        verify(token, never()).split(anyList());
    }

    @Test
    void rejectsMissingOrDuplicateStrategyImplementations() {
        DocumentSplittingStrategy token = mock(DocumentSplittingStrategy.class);
        DocumentSplittingStrategy duplicate =
            mock(DocumentSplittingStrategy.class);
        when(token.type()).thenReturn(ChunkingStrategy.TOKEN);
        when(duplicate.type()).thenReturn(ChunkingStrategy.TOKEN);
        VectorDocumentIdGenerator idGenerator =
            new VectorDocumentIdGenerator();

        assertThatThrownBy(() -> new DocumentChunker(List.of(token), idGenerator))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("RECURSIVE");
        assertThatThrownBy(() -> new DocumentChunker(
            List.of(token, duplicate),
            idGenerator
        )).isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("TOKEN");
    }

    private DocumentChunker chunker(
        RagProperties properties,
        VectorDocumentIdGenerator idGenerator
    ) {
        return new DocumentChunker(
            List.of(
                new TokenDocumentSplittingStrategy(properties),
                new RecursiveDocumentSplittingStrategy(
                    properties,
                    new Cl100kTokenCodec()
                )
            ),
            idGenerator
        );
    }
}
