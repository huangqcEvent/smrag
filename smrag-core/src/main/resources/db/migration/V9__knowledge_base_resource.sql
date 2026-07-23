CREATE TABLE IF NOT EXISTS knowledge_base (
    code VARCHAR(64) PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(500),
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_knowledge_base_status
        CHECK (status IN ('ACTIVE', 'DISABLED'))
);

INSERT INTO knowledge_base (code, name, description, status)
VALUES ('company-main', '企业知识库', '企业内部统一知识库', 'ACTIVE')
ON CONFLICT (code) DO UPDATE
SET name = EXCLUDED.name,
    description = EXCLUDED.description,
    status = EXCLUDED.status,
    updated_at = CURRENT_TIMESTAMP;

UPDATE knowledge_document
SET knowledge_base_id = 'company-main'
WHERE knowledge_base_id IS NULL
   OR BTRIM(knowledge_base_id) = ''
   OR knowledge_base_id = 'default';

INSERT INTO knowledge_base (code, name, description, status)
SELECT DISTINCT
    knowledge_base_id,
    knowledge_base_id,
    '由历史知识库标识迁移',
    'ACTIVE'
FROM knowledge_document
WHERE knowledge_base_id <> 'company-main'
ON CONFLICT (code) DO NOTHING;

UPDATE rag_vector_store vector
SET metadata = jsonb_set(
    vector.metadata::jsonb,
    '{knowledgeBaseId}',
    to_jsonb(document.knowledge_base_id),
    true
)
FROM knowledge_document document
WHERE vector.metadata->>'documentId' = document.id::text
  AND vector.metadata->>'knowledgeBaseId'
      IS DISTINCT FROM document.knowledge_base_id;

ALTER TABLE knowledge_document
    ALTER COLUMN knowledge_base_id SET DEFAULT 'company-main';

CREATE INDEX IF NOT EXISTS idx_knowledge_document_base
    ON knowledge_document(knowledge_base_id);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_knowledge_document_base'
          AND conrelid = 'public.knowledge_document'::regclass
    ) THEN
        ALTER TABLE knowledge_document
            ADD CONSTRAINT fk_knowledge_document_base
            FOREIGN KEY (knowledge_base_id)
            REFERENCES knowledge_base(code);
    END IF;
END $$;
