-- ADMIN is a global authorization bypass. Keeping it in resource ACLs is
-- redundant and makes an empty business ACL look valid.
WITH removed_permissions AS (
    DELETE FROM doc_permission permission
    USING sys_role role
    WHERE permission.role_id = role.id
      AND role.role_code = 'ADMIN'
    RETURNING permission.document_id
),
updated_documents AS (
    UPDATE knowledge_document document
    SET acl_version = COALESCE(document.acl_version, 0) + 1,
        update_time = CURRENT_TIMESTAMP
    WHERE document.id IN (
        SELECT DISTINCT document_id
        FROM removed_permissions
    )
    RETURNING document.id, document.acl_version
)
UPDATE rag_vector_store vector
SET metadata = (
    (
        SELECT COALESCE(
            jsonb_object_agg(entry.key, entry.value)
                FILTER (
                    WHERE entry.key NOT LIKE 'aclRole\_%' ESCAPE '\'
                      AND entry.key NOT LIKE 'aclRoleId\_%' ESCAPE '\'
                ),
            '{}'::jsonb
        )
        FROM jsonb_each(vector.metadata::jsonb) entry
    ) || jsonb_build_object(
        'documentAclVersion',
        updated_document.acl_version
    )
)::json
FROM updated_documents updated_document
WHERE vector.document_id = updated_document.id;

DELETE FROM knowledge_base_role base_role
USING sys_role role
WHERE base_role.role_id = role.id
  AND role.role_code = 'ADMIN';

-- An active knowledge base must always have at least one configurable role.
UPDATE knowledge_base knowledge_base
SET status = 'DISABLED',
    version = knowledge_base.version + 1,
    updated_at = CURRENT_TIMESTAMP
WHERE knowledge_base.status = 'ACTIVE'
  AND NOT EXISTS (
      SELECT 1
      FROM knowledge_base_role base_role
      WHERE base_role.knowledge_base_id = knowledge_base.id
  );
