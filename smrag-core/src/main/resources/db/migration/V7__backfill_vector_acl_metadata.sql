WITH document_acl AS (
    SELECT
        d.id AS document_id,
        jsonb_build_object(
            'aclVersion', d.acl_version,
            'aclDenied', COUNT(r.id) = 0
        ) || COALESCE(
            jsonb_object_agg('aclRole_' || r.role_code, true)
                FILTER (WHERE r.id IS NOT NULL),
            '{}'::jsonb
        ) AS acl_metadata
    FROM knowledge_document d
    LEFT JOIN doc_permission dp
        ON dp.document_id = d.id AND dp.can_read = true
    LEFT JOIN sys_role r ON r.id = dp.role_id
    GROUP BY d.id, d.acl_version
)
UPDATE rag_vector_store vector
SET metadata = (
    (
        SELECT COALESCE(
            jsonb_object_agg(entry.key, entry.value)
                FILTER (WHERE entry.key NOT LIKE 'aclRole\_%' ESCAPE '\'),
            '{}'::jsonb
        )
        FROM jsonb_each(vector.metadata::jsonb) entry
    ) || document_acl.acl_metadata
)::json
FROM document_acl
WHERE vector.metadata->>'documentId' = document_acl.document_id::text;
