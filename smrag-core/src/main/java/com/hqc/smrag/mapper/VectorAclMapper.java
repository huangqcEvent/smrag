package com.hqc.smrag.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface VectorAclMapper {

    @Update("""
        UPDATE rag_vector_store
        SET metadata = (
            (
                SELECT COALESCE(
                    jsonb_object_agg(entry.key, entry.value)
                        FILTER (
                            WHERE entry.key NOT LIKE 'aclRole\\_%' ESCAPE '\\'
                              AND entry.key NOT LIKE 'aclRoleId\\_%' ESCAPE '\\'
                        ),
                    '{}'::jsonb
                )
                FROM jsonb_each(metadata::jsonb) entry
            ) || jsonb_build_object(
                'documentAclVersion', CAST(#{aclVersion} AS integer)
            )
        )::json
        WHERE metadata->>'documentId' = CAST(#{documentId} AS text)
        """)
    int updateDocumentAcl(
        @Param("documentId") Long documentId,
        @Param("aclVersion") Integer aclVersion
    );

    @Update("""
        UPDATE rag_vector_store
        SET metadata = jsonb_set(
            metadata::jsonb,
            '{knowledgeBaseId}',
            to_jsonb(CAST(#{knowledgeBaseId} AS bigint)),
            true
        )::json
        WHERE document_id = #{documentId}
        """)
    int updateKnowledgeBase(
        @Param("documentId") Long documentId,
        @Param("knowledgeBaseId") Long knowledgeBaseId
    );
}
