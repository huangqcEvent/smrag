package com.hqc.smrag.service.knowledge;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentReader;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;

@Component
public class SpringAiDocumentReader {

    public List<Document> read(Path path, String fileType) {
        FileSystemResource resource = new FileSystemResource(path);
        DocumentReader reader = "PDF".equalsIgnoreCase(fileType)
            ? new PagePdfDocumentReader(resource)
            : new TikaDocumentReader(resource);
        return reader.get();
    }
}
