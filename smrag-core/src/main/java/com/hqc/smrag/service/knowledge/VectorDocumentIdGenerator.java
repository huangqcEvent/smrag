package com.hqc.smrag.service.knowledge;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * 向量文档ID生成器
 * <p>
 * 为文档分块生成唯一且可重现的向量ID。
 * <p>
 * 生成策略：基于知识库ID、文档ID、版本号、分块索引的组合，使用 UUID v5（命名空间）生成。
 * <p>
 * 这种方式确保：
 * <ul>
 *     <li>唯一性：不同的分块有不同的ID</li>
 *     <li>可重现性：相同参数总是生成相同的ID</li>
 *     <li>确定性：不需要存储ID映射关系</li>
 * </ul>
 * <p>
 * 生成的 ID 格式：UUID.nameUUIDFromBytes("knowledgeBaseId/documentId/version/chunkIndex")
 */
@Component
public class VectorDocumentIdGenerator {

    /**
     * 生成向量文档ID
     * <p>
     * 根据知识库ID、文档ID、版本号、分块索引生成唯一的 UUID。
     *
     * @param knowledgeBaseId 知识库ID
     * @param documentId      文档ID
     * @param version         文档版本号
     * @param chunkIndex      分块索引
     * @return 生成的 UUID
     */
    public UUID generate(String knowledgeBaseId, long documentId, int version, int chunkIndex) {
        String key = knowledgeBaseId + "/" + documentId + "/" + version + "/" + chunkIndex;
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
    }
}
