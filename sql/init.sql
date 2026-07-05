-- 创建数据库（需先手动创建或使用超级用户执行）
-- CREATE DATABASE smrag;

-- 启用PGVector扩展
CREATE EXTENSION IF NOT EXISTS vector;

-- 1. 文档表
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

-- 2. 文档切片表
CREATE TABLE IF NOT EXISTS knowledge_chunk (
    id BIGSERIAL PRIMARY KEY,
    document_id BIGINT,
    content TEXT,
    chunk_index INT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 3. 产品表
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

-- 4. 管理人表
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

-- 5. 会话表
CREATE TABLE IF NOT EXISTS chat_session (
    id BIGSERIAL PRIMARY KEY,
    session_id VARCHAR(100),
    user_id BIGINT,
    question TEXT,
    rewrite_question TEXT,
    answer TEXT,
    route_type VARCHAR(50),
    references TEXT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 6. 向量存储表
CREATE TABLE IF NOT EXISTS vector_store (
    id BIGSERIAL PRIMARY KEY,
    content TEXT,
    embedding VECTOR(1536),
    metadata JSONB,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 创建向量索引
CREATE INDEX IF NOT EXISTS vector_embedding_idx ON vector_store USING ivfflat (embedding vector_cosine_ops);

-- 插入测试数据
INSERT INTO fund_manager (manager_code, manager_name, company_name, assets_scale) VALUES
('M001', '华映资本', '华映资本管理有限公司', 5000000000.00),
('M002', '红杉中国', '红杉资本中国基金', 20000000000.00);

INSERT INTO fund_product (product_code, product_name, manager_id, risk_level, return_rate, min_amount) VALUES
('P001', '华映成长一期', 1, 'R4', 15.5, 1000000.00),
('P002', '红杉创新基金', 2, 'R5', 18.2, 5000000.00);