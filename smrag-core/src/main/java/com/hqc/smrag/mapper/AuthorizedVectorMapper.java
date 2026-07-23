package com.hqc.smrag.mapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hqc.smrag.mapper.projection.AuthorizedVectorRow;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class AuthorizedVectorMapper {

    private static final String SEARCH_SQL = """
        SELECT
            v.id::text AS vector_id,
            v.content,
            v.metadata::text AS metadata_json,
            1 - (v.embedding <=> CAST(? AS vector)) AS score,
            kb.id AS knowledge_base_id,
            kb.code AS knowledge_base_code,
            kb.name AS knowledge_base_name
        FROM rag_vector_store v
        INNER JOIN knowledge_document d
            ON d.id = v.document_id
           AND d.knowledge_base_id = v.knowledge_base_id
        INNER JOIN knowledge_base kb
            ON kb.id = v.knowledge_base_id
        WHERE kb.id = ?
          AND kb.status = 'ACTIVE'
          AND d.status = 'COMPLETED'
          AND v.document_version = d.version
          AND v.document_acl_version = d.acl_version
          AND (CAST(? AS bigint) IS NULL OR d.id = CAST(? AS bigint))
          AND EXISTS (
              SELECT 1
              FROM knowledge_base_role kbr
              WHERE kbr.knowledge_base_id = kb.id
          )
          AND EXISTS (
              SELECT 1
              FROM doc_permission dp
              WHERE dp.document_id = d.id
                AND dp.can_read = true
          )
          AND (
              ? = true
              OR (
                  EXISTS (
                      SELECT 1
                      FROM knowledge_base_role kbr
                      INNER JOIN sys_user_role sur ON sur.role_id = kbr.role_id
                      WHERE kbr.knowledge_base_id = kb.id
                        AND sur.user_id = ?
                  )
                  AND EXISTS (
                      SELECT 1
                      FROM doc_permission dp
                      INNER JOIN sys_user_role sur ON sur.role_id = dp.role_id
                      WHERE dp.document_id = d.id
                        AND dp.can_read = true
                        AND sur.user_id = ?
                  )
              )
          )
          AND 1 - (v.embedding <=> CAST(? AS vector)) >= ?
        ORDER BY v.embedding <=> CAST(? AS vector)
        LIMIT ?
        """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<AuthorizedVectorRow> search(
        Long knowledgeBaseId,
        Long userId,
        boolean globalAdmin,
        Long documentId,
        float[] embedding,
        double similarityThreshold,
        int topK
    ) {
        configureIterativeScan();
        String vector = toVectorLiteral(embedding);
        return jdbcTemplate.query(
            SEARCH_SQL,
            (result, rowNum) -> {
                Map<String, Object> metadata;
                try {
                    metadata = objectMapper.readValue(
                        result.getString("metadata_json"),
                        new TypeReference<>() {
                        }
                    );
                } catch (Exception failure) {
                    throw new IllegalStateException("向量元数据格式不合法", failure);
                }
                return new AuthorizedVectorRow(
                    result.getString("vector_id"),
                    result.getString("content"),
                    metadata,
                    result.getDouble("score"),
                    result.getLong("knowledge_base_id"),
                    result.getString("knowledge_base_code"),
                    result.getString("knowledge_base_name")
                );
            },
            vector,
            knowledgeBaseId,
            documentId,
            documentId,
            globalAdmin,
            userId,
            userId,
            vector,
            similarityThreshold,
            vector,
            topK
        );
    }

    /**
     * pgvector's iterative HNSW scan continues visiting index candidates after
     * relational KB/RBAC filters discard early matches. SET LOCAL keeps the
     * tuning scoped to this read transaction and avoids leaking connection
     * state through the pool.
     */
    private void configureIterativeScan() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            return;
        }
        jdbcTemplate.execute("SET LOCAL hnsw.iterative_scan = strict_order");
        jdbcTemplate.execute("SET LOCAL hnsw.max_scan_tuples = 20000");
    }

    private String toVectorLiteral(float[] embedding) {
        if (embedding == null || embedding.length == 0) {
            throw new IllegalArgumentException("问题向量不能为空");
        }
        StringBuilder value = new StringBuilder(embedding.length * 10).append('[');
        for (int index = 0; index < embedding.length; index++) {
            if (index > 0) {
                value.append(',');
            }
            value.append(Float.toString(embedding[index]));
        }
        return value.append(']').toString();
    }
}
