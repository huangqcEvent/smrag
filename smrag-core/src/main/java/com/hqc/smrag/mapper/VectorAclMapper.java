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
                        FILTER (WHERE entry.key NOT LIKE 'aclRole\\_%' ESCAPE '\\'),
                    '{}'::jsonb
                )
                FROM jsonb_each(metadata::jsonb) entry
            ) || CAST(#{aclMetadata} AS jsonb)
        )::json
        WHERE metadata->>'documentId' = CAST(#{documentId} AS text)
        """)
    int updateDocumentAcl(
        @Param("documentId") Long documentId,
        @Param("aclMetadata") String aclMetadata
    );
}
