CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS hstore;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

ALTER TABLE knowledge_document
    ADD COLUMN IF NOT EXISTS knowledge_base_id VARCHAR(64) NOT NULL DEFAULT 'default',
    ADD COLUMN IF NOT EXISTS checksum VARCHAR(64),
    ADD COLUMN IF NOT EXISTS version INT NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS failure_reason TEXT,
    ADD COLUMN IF NOT EXISTS processing_token UUID DEFAULT uuid_generate_v4(),
    ADD COLUMN IF NOT EXISTS processing_started_at TIMESTAMP;

ALTER TABLE knowledge_chunk
    ADD COLUMN IF NOT EXISTS vector_id UUID,
    ADD COLUMN IF NOT EXISTS page_number INT,
    ADD COLUMN IF NOT EXISTS document_version INT NOT NULL DEFAULT 1;

CREATE UNIQUE INDEX IF NOT EXISTS uk_knowledge_chunk_vector_id
    ON knowledge_chunk(vector_id)
    WHERE vector_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS rag_vector_store (
    id UUID DEFAULT uuid_generate_v4() PRIMARY KEY,
    content TEXT,
    metadata JSON,
    embedding VECTOR(1536)
);

CREATE INDEX IF NOT EXISTS rag_vector_store_embedding_hnsw_idx
    ON rag_vector_store USING HNSW (embedding vector_cosine_ops);

CREATE TABLE IF NOT EXISTS spring_ai_chat_memory (
    conversation_id VARCHAR(36) NOT NULL,
    content TEXT NOT NULL,
    type VARCHAR(10) NOT NULL CHECK (type IN ('USER', 'ASSISTANT', 'SYSTEM', 'TOOL')),
    "timestamp" TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS spring_ai_chat_memory_conversation_id_timestamp_idx
    ON spring_ai_chat_memory(conversation_id, "timestamp");

DO $$
DECLARE
    embedding_type TEXT;
BEGIN
    SELECT format_type(attribute.atttypid, attribute.atttypmod)
    INTO embedding_type
    FROM pg_attribute attribute
    JOIN pg_class table_info ON table_info.oid = attribute.attrelid
    JOIN pg_namespace schema_info ON schema_info.oid = table_info.relnamespace
    WHERE schema_info.nspname = 'public'
      AND table_info.relname = 'rag_vector_store'
      AND attribute.attname = 'embedding'
      AND NOT attribute.attisdropped;

    IF embedding_type IS DISTINCT FROM 'vector(1536)' THEN
        RAISE EXCEPTION 'rag_vector_store.embedding must be vector(1536), found %', embedding_type;
    END IF;
END $$;
