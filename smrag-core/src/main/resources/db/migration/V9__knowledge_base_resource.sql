-- V9 intentionally resets only the knowledge/RAG domain and creates no
-- implicit knowledge base, while users, roles and chat history remain intact.
TRUNCATE TABLE
    document_ingestion_outbox,
    doc_permission,
    knowledge_chunk,
    rag_vector_store,
    knowledge_document
CASCADE;

-- Old documents no longer exist after this intentional reset. Remove their
-- persisted citations and keep the document sequence monotonic so an old
-- document id can never be rebound to a different file.
UPDATE chat_session
SET reference_docs = '[]'
WHERE reference_docs IS NOT NULL
  AND reference_docs <> '[]';

-- Internal memory keys are "{userId}:{clientSessionId}". The request contract
-- allows a 128-character client session id, so the legacy VARCHAR(36) is too
-- small once account isolation is applied.
ALTER TABLE spring_ai_chat_memory
    ALTER COLUMN conversation_id TYPE VARCHAR(160);

ALTER TABLE knowledge_document
    DROP CONSTRAINT IF EXISTS fk_knowledge_document_base;

DROP TABLE IF EXISTS knowledge_base_role;
DROP TABLE IF EXISTS knowledge_base CASCADE;

ALTER TABLE knowledge_document
    DROP COLUMN IF EXISTS knowledge_base_id;

CREATE TABLE knowledge_base (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(500),
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    version INTEGER NOT NULL DEFAULT 1,
    created_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_knowledge_base_creator
        FOREIGN KEY (created_by) REFERENCES sys_user(id),
    CONSTRAINT chk_knowledge_base_status
        CHECK (status IN ('ACTIVE', 'DISABLED'))
);

CREATE TABLE knowledge_base_role (
    knowledge_base_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    created_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (knowledge_base_id, role_id),
    CONSTRAINT fk_knowledge_base_role_base
        FOREIGN KEY (knowledge_base_id) REFERENCES knowledge_base(id) ON DELETE CASCADE,
    CONSTRAINT fk_knowledge_base_role_role
        FOREIGN KEY (role_id) REFERENCES sys_role(id),
    CONSTRAINT fk_knowledge_base_role_creator
        FOREIGN KEY (created_by) REFERENCES sys_user(id)
);

ALTER TABLE knowledge_document
    ADD COLUMN knowledge_base_id BIGINT NOT NULL,
    ADD CONSTRAINT fk_knowledge_document_base
        FOREIGN KEY (knowledge_base_id) REFERENCES knowledge_base(id);

CREATE INDEX idx_knowledge_base_status
    ON knowledge_base(status);

CREATE INDEX idx_knowledge_base_role_role_base
    ON knowledge_base_role(role_id, knowledge_base_id);

CREATE INDEX idx_knowledge_document_base
    ON knowledge_document(knowledge_base_id);

ALTER TABLE rag_vector_store
    ADD COLUMN knowledge_base_id BIGINT
        GENERATED ALWAYS AS (
            NULLIF(metadata->>'knowledgeBaseId', '')::BIGINT
        ) STORED,
    ADD COLUMN document_id BIGINT
        GENERATED ALWAYS AS (
            NULLIF(metadata->>'documentId', '')::BIGINT
        ) STORED,
    ADD COLUMN document_acl_version INTEGER
        GENERATED ALWAYS AS (
            NULLIF(metadata->>'documentAclVersion', '')::INTEGER
        ) STORED,
    ADD COLUMN document_version INTEGER
        GENERATED ALWAYS AS (
            NULLIF(metadata->>'documentVersion', '')::INTEGER
        ) STORED;

CREATE INDEX idx_rag_vector_store_base
    ON rag_vector_store(knowledge_base_id);

CREATE INDEX idx_rag_vector_store_document
    ON rag_vector_store(document_id);

CREATE INDEX idx_rag_vector_store_acl
    ON rag_vector_store(
        knowledge_base_id,
        document_id,
        document_version,
        document_acl_version
    );
