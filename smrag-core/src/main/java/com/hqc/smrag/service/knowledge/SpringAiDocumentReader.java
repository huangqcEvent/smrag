package com.hqc.smrag.service.knowledge;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentReader;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;

/**
 * Spring AI 文档阅读器
 * <p>
 * 基于 Spring AI 的文档阅读器，支持读取不同格式的文档文件。
 * <p>
 * 支持的文档格式：
 * <ul>
 *     <li>PDF：使用 {@link PagePdfDocumentReader} 按页读取</li>
 *     <li>Word、TXT 等：使用 {@link TikaDocumentReader} 读取</li>
 * </ul>
 * <p>
 * 返回的 {@link Document} 对象包含文档内容和元数据（如页码）。
 *
 * @see DocumentReader Spring AI 文档阅读器接口
 * @see Document Spring AI 文档对象
 */
@Component
public class SpringAiDocumentReader {

    /**
     * 读取文档文件
     * <p>
     * 根据文件类型选择合适的阅读器：
     * <ul>
     *     <li>PDF 文件：使用 PagePdfDocumentReader，按页拆分</li>
     *     <li>其他文件：使用 TikaDocumentReader，支持多种格式</li>
     * </ul>
     *
     * @param path     文件路径
     * @param fileType 文件类型（PDF、WORD、TXT）
     * @return 文档列表，每个文档对应文件中的一个片段（页或段落）
     */
    public List<Document> read(Path path, String fileType) {
        FileSystemResource resource = new FileSystemResource(path);
        DocumentReader reader = "PDF".equalsIgnoreCase(fileType)
            ? new PagePdfDocumentReader(resource)
            : new TikaDocumentReader(resource);
        return reader.get();
    }
}
