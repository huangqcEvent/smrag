ALTER TABLE knowledge_document
    ADD COLUMN IF NOT EXISTS storage_type VARCHAR(16) NOT NULL DEFAULT 'LOCAL';

ALTER TABLE knowledge_document
    ADD COLUMN IF NOT EXISTS object_key VARCHAR(1024);

ALTER TABLE knowledge_document
    ADD COLUMN IF NOT EXISTS parsed_text_object_key VARCHAR(1024);

ALTER TABLE knowledge_document
    ADD COLUMN IF NOT EXISTS acl_version INTEGER NOT NULL DEFAULT 1;

CREATE INDEX IF NOT EXISTS idx_doc_permission_role_document
    ON doc_permission(role_id, document_id)
    WHERE can_read = TRUE;
