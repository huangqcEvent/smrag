package com.hqc.smrag.service.knowledge;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.document.Document;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class SpringAiDocumentReaderTest {

    @TempDir
    Path tempDir;

    @Test
    void readsDocxWithTheRuntimeCommonsCompressVersion() throws IOException {
        Path docx = tempDir.resolve("enterprise-rag.docx");
        writeMinimalDocx(docx, "Enterprise RAG knowledge base");

        List<Document> documents = new SpringAiDocumentReader().read(docx, "WORD");

        assertThat(documents)
            .extracting(Document::getText)
            .anySatisfy(text -> assertThat(text).contains("Enterprise RAG knowledge base"));
    }

    private void writeMinimalDocx(Path path, String text) throws IOException {
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(path))) {
            writeEntry(output, "[Content_Types].xml", """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                  <Default Extension="xml" ContentType="application/xml"/>
                  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
                </Types>
                """);
            writeEntry(output, "_rels/.rels", """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
                </Relationships>
                """);
            writeEntry(output, "word/document.xml", """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                  <w:body><w:p><w:r><w:t>%s</w:t></w:r></w:p></w:body>
                </w:document>
                """.formatted(text));
        }
    }

    private void writeEntry(ZipOutputStream output, String name, String content) throws IOException {
        output.putNextEntry(new ZipEntry(name));
        output.write(content.getBytes(StandardCharsets.UTF_8));
        output.closeEntry();
    }
}
