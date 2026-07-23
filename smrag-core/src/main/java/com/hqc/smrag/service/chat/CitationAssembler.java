package com.hqc.smrag.service.chat;

import com.hqc.smrag.dto.CitationDTO;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 引用组装器
 * <p>
 * 将 Spring AI 返回的文档列表转换为前端可展示的引用信息。
 * 主要功能：
 * <ul>
 *     <li>去重：避免重复引用同一文档片段</li>
 *     <li>截断：限制摘要长度，避免过长</li>
 *     <li>数量限制：控制引用数量，避免过多</li>
 *     <li>元数据提取：提取文档ID、知识库ID、页码等信息</li>
 * </ul>
 *
 * @see CitationDTO 引用 DTO
 * @see Document Spring AI 文档对象
 */
@Component
public class CitationAssembler {

    /**
     * 摘要最大长度（字符数）
     */
    private static final int MAX_EXCERPT_LENGTH = 180;

    /**
     * 组装引用列表
     * <p>
     * 处理步骤：
     * <ol>
     *     <li>遍历文档列表，按顺序添加引用</li>
     *     <li>通过向量ID去重，避免重复引用</li>
     *     <li>达到数量限制后停止添加</li>
     *     <li>截断文档内容作为摘要</li>
     * </ol>
     *
     * @param documents 文档列表
     * @param limit     最大引用数量
     * @return 引用 DTO 列表
     */
    public List<CitationDTO> assemble(List<Document> documents, int limit) {
        if (documents == null || documents.isEmpty() || limit <= 0) {
            return List.of();
        }
        List<CitationDTO> citations = new ArrayList<>();
        Set<String> seenVectorIds = new HashSet<>();
        for (Document document : documents) {
            if (citations.size() >= limit) {
                break;
            }
            if (!seenVectorIds.add(document.getId())) {
                continue;
            }
            Map<String, Object> metadata = document.getMetadata();
            citations.add(new CitationDTO(
                document.getId(),
                asLong(metadata.get("documentId")),
                asLong(metadata.get("knowledgeBaseId")),
                asString(metadata.get("knowledgeBaseCode")),
                asString(metadata.get("knowledgeBaseName")),
                asString(metadata.get("documentName")),
                asInteger(metadata.get("chunkIndex")),
                asInteger(metadata.get("pageNumber")),
                document.getScore(),
                excerpt(document.getText())
            ));
        }
        return List.copyOf(citations);
    }

    /**
     * 截取文档内容作为摘要
     * <p>
     * 将多余的空白字符替换为单个空格，并截断到指定长度。
     *
     * @param text 文档内容
     * @return 截断后的摘要
     */
    private String excerpt(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String normalized = text.replaceAll("\\s+", " ").trim();
        return normalized.length() <= MAX_EXCERPT_LENGTH
            ? normalized
            : normalized.substring(0, MAX_EXCERPT_LENGTH);
    }

    /**
     * 转换为字符串
     */
    private String asString(Object value) {
        return value == null ? null : value.toString();
    }

    /**
     * 转换为 Long 类型
     */
    private Long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return value == null ? null : Long.valueOf(value.toString());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    /**
     * 转换为 Integer 类型
     */
    private Integer asInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? null : Integer.valueOf(value.toString());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
