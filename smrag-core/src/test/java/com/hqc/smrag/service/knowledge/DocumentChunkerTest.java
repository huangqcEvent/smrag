package com.hqc.smrag.service.knowledge;

import com.hqc.smrag.config.RagProperties;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentChunkerTest {

    @Test
    void preservesBusinessMetadataAndAssignsStableVectorIds() {
        RagProperties properties = new RagProperties();
        properties.setChunkSize(30);
        properties.setMinChunkSizeChars(20);
        properties.setMinChunkLengthToEmbed(1);
        properties.setMaxNumChunks(20);

        VectorDocumentIdGenerator idGenerator = new VectorDocumentIdGenerator();
        DocumentChunker chunker = new DocumentChunker(properties, idGenerator);
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
            "default",
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
                .containsEntry("knowledgeBaseId", "default")
                .containsEntry("documentId", 42L)
                .containsEntry("documentName", "enterprise-rag.pdf")
                .containsEntry("documentType", "PDF")
                .containsEntry("documentVersion", 2)
                .containsEntry("documentStatus", "PROCESSING")
                .containsEntry("checksum", "checksum-42")
                .containsEntry("aclVersion", 3)
                .containsEntry("aclDenied", false)
                .containsEntry("aclRoleId_1", true)
                .doesNotContainKey("aclRole_1")
                .containsKey("chunkIndex");
        });
        assertThat(chunks)
            .anySatisfy(chunk -> assertThat(chunk.getMetadata()).containsEntry("pageNumber", 7));

        List<Document> repeated = chunker.chunk(
            sources,
            "default",
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
    void writesSameAclMetadataToEveryChunk() {
        RagProperties properties = new RagProperties();
        properties.setChunkSize(30);
        properties.setMinChunkSizeChars(1);
        properties.setMinChunkLengthToEmbed(1);
        DocumentChunker chunker =
            new DocumentChunker(properties, new VectorDocumentIdGenerator());

        List<Document> chunks = chunker.chunk(
            List.of(new Document("permission aware knowledge content")),
            "default", 42L, "manual.txt", "TXT", 1, "COMPLETED",
            "checksum", 3, Set.of(1L, 5L));

        assertThat(chunks).isNotEmpty().allSatisfy(chunk ->
            assertThat(chunk.getMetadata())
                .containsEntry("aclVersion", 3)
                .containsEntry("aclDenied", false)
                .containsEntry("aclRoleId_1", true)
                .containsEntry("aclRoleId_5", true)
                .doesNotContainKeys("aclRole_1", "aclRole_5"));
    }
}
