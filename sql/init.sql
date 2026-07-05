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
    reference_docs TEXT,
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
INSERT INTO fund_manager (manager_code, manager_name, company_name, assets_scale, qualification) VALUES
('M001', '华映资本', '华映资本管理有限公司', 5000000000.00, '中国证券投资基金业协会备案管理人，擅长TMT、消费领域投资'),
('M002', '红杉中国', '红杉资本中国基金', 20000000000.00, '顶级VC机构，专注科技、医疗、消费赛道'),
('M003', '高瓴资本', '高瓴资本管理有限公司', 30000000000.00, '全球知名投资机构，覆盖一级市场和二级市场'),
('M004', '景林资产', '景林资产管理有限公司', 8000000000.00, '价值投资专家，长期业绩优秀');

INSERT INTO fund_product (product_code, product_name, manager_id, risk_level, return_rate, investment_strategy, min_amount) VALUES
('P001', '华映成长一期', 1, 'R4', 15.5, '专注科技和消费领域成长型企业投资', 1000000.00),
('P002', '红杉创新基金', 2, 'R5', 18.2, '科技创新赛道，布局AI、新能源、生物医药', 5000000.00),
('P003', '高瓴价值精选', 3, 'R3', 12.8, '长期价值投资，配置消费医疗龙头', 3000000.00),
('P004', '景林优选', 4, 'R4', 14.6, 'A股价值投资，聚焦核心资产', 2000000.00),
('P005', '华映稳健收益', 1, 'R2', 8.5, '固定收益+少量权益，稳健型产品', 500000.00);

-- 插入知识库测试数据
INSERT INTO knowledge_document (name, file_type, status, chunk_count) VALUES
('私募基金投资指南.pdf', 'PDF', 'COMPLETED', 3),
('产品风险等级说明.docx', 'WORD', 'COMPLETED', 2);

INSERT INTO knowledge_chunk (document_id, content, chunk_index) VALUES
(1, '私募基金是指以非公开方式向特定投资者募集资金并进行投资管理的基金。私募基金投资具有较高的风险，投资者需要具备相应的风险识别能力和风险承受能力。', 0),
(1, '私募基金投资策略包括：股票多头策略、量化策略、宏观对冲策略、固定收益策略等。不同策略具有不同的风险收益特征，投资者应根据自身情况选择合适的产品。', 1),
(1, '私募基金最低投资门槛通常为100万元人民币，部分产品要求300万或500万起投。投资者需确保资金来源合法合规。', 2),
(2, '私募基金风险等级分为R1-R5五个等级：R1为低风险，R2为中低风险，R3为中风险，R4为中高风险，R5为高风险。投资者应选择与自身风险承受能力匹配的产品。', 0),
(2, '投资私募基金前，投资者需要进行风险测评，了解自己的风险承受能力等级。管理人应当向投资者充分揭示产品风险，确保投资者知情同意。', 1);