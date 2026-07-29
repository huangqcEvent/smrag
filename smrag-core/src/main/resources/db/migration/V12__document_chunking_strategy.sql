ALTER TABLE knowledge_document
    ADD COLUMN chunking_strategy VARCHAR(16) NOT NULL DEFAULT 'TOKEN';

ALTER TABLE knowledge_document
    ADD CONSTRAINT chk_knowledge_document_chunking_strategy
        CHECK (chunking_strategy IN ('TOKEN', 'RECURSIVE'));
