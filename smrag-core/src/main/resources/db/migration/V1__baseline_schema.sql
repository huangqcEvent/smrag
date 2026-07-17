CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS knowledge_document (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255),
    file_path VARCHAR(500),
    file_type VARCHAR(50),
    status VARCHAR(20) DEFAULT 'PENDING',
    chunk_count INT DEFAULT 0,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS knowledge_chunk (
    id BIGSERIAL PRIMARY KEY,
    document_id BIGINT,
    content TEXT,
    chunk_index INT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS fund_product (
    id BIGSERIAL PRIMARY KEY,
    product_code VARCHAR(100),
    product_name VARCHAR(255),
    manager_id BIGINT,
    risk_level VARCHAR(20),
    return_rate DECIMAL(10,4),
    investment_strategy TEXT,
    min_amount DECIMAL(20,2),
    status VARCHAR(20) DEFAULT 'ACTIVE',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS fund_manager (
    id BIGSERIAL PRIMARY KEY,
    manager_code VARCHAR(100),
    manager_name VARCHAR(255),
    company_name VARCHAR(255),
    register_time TIMESTAMP,
    assets_scale DECIMAL(20,2),
    qualification TEXT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS chat_session (
    id BIGSERIAL PRIMARY KEY,
    session_id VARCHAR(100),
    user_id BIGINT,
    question TEXT,
    rewrite_question TEXT,
    answer TEXT,
    route_type VARCHAR(50),
    reference_docs TEXT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS vector_store (
    id BIGSERIAL PRIMARY KEY,
    content TEXT,
    embedding VECTOR(1536),
    metadata JSONB,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS vector_embedding_idx
    ON vector_store USING IVFFLAT (embedding vector_cosine_ops);
