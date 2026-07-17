package com.hqc.smrag.service.knowledge;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 向量服务
 * <p>
 * 封装 Spring AI 的 VectorStore 组件，提供向量存储和检索能力。
 * 主要功能：
 * <ul>
 *     <li>存储向量：将文本内容转换为向量并存储</li>
 *     <li>相似度搜索：根据查询文本检索相似的文档</li>
 * </ul>
 * <p>
 * 底层使用 PostgreSQL + PGVector 扩展存储向量数据。
 *
 * @see VectorStore Spring AI 向量存储接口
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VectorService {

    private final VectorStore vectorStore;

    /**
     * 存储单个向量
     * <p>
     * 将文本内容转换为向量，连同元数据一起存储到向量数据库。
     *
     * @param content  文本内容
     * @param metadata 元数据
     */
    public void storeVector(String content, Map<String, Object> metadata) {
        Document document = new Document(content, metadata);
        vectorStore.add(List.of(document));
        log.info("存储向量完成");
    }

    /**
     * 批量存储向量
     * <p>
     * 将多个文本内容转换为向量，使用相同的元数据批量存储。
     *
     * @param contents    文本内容列表
     * @param baseMetadata 基础元数据（所有文档共享）
     */
    public void storeVectors(List<String> contents, Map<String, Object> baseMetadata) {
        List<Document> documents = contents.stream()
            .map(content -> new Document(content, baseMetadata))
            .collect(Collectors.toList());
        vectorStore.add(documents);
        log.info("批量存储向量完成，数量: {}", documents.size());
    }

    /**
     * 相似度搜索
     * <p>
     * 根据查询文本，从向量数据库中检索最相似的 topK 个文档。
     *
     * @param query 查询文本
     * @param topK  返回结果数量
     * @return 相似文档列表
     */
    public List<Document> searchSimilar(String query, int topK) {
        SearchRequest request = SearchRequest.builder()
            .query(query)
            .topK(topK)
            .build();
        List<Document> results = vectorStore.similaritySearch(request);
        log.info("搜索完成，返回结果数: {}", results.size());
        return results;
    }
}
