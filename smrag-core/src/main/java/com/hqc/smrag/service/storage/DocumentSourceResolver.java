package com.hqc.smrag.service.storage;

import com.hqc.smrag.entity.KnowledgeDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class DocumentSourceResolver {

    private final DocumentStorage storage;

    public ResolvedSource resolve(KnowledgeDocument document) {
        if ("MINIO".equalsIgnoreCase(document.getStorageType())) {
            Path path = storage.materialize(document.getObjectKey(), suffix(document));
            return new ResolvedSource(path, true);
        }
        if (document.getFilePath() == null || document.getFilePath().isBlank()) {
            throw new IllegalStateException("Document does not have a readable source");
        }
        return new ResolvedSource(Path.of(document.getFilePath()), false);
    }

    private String suffix(KnowledgeDocument document) {
        String name = document.getName();
        if (name != null) {
            int dot = name.lastIndexOf('.');
            if (dot >= 0) {
                return name.substring(dot).toLowerCase(Locale.ROOT);
            }
        }
        return switch (String.valueOf(document.getFileType()).toUpperCase(Locale.ROOT)) {
            case "PDF" -> ".pdf";
            case "WORD" -> ".docx";
            case "TXT" -> ".txt";
            default -> ".tmp";
        };
    }

    public record ResolvedSource(Path path, boolean temporary) implements AutoCloseable {

        @Override
        public void close() throws IOException {
            if (temporary) {
                Files.deleteIfExists(path);
            }
        }
    }
}
