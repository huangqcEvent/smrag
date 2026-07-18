-- =====================================================
-- 认证权限系统数据库迁移脚本
-- 创建用户、角色、权限相关表
-- =====================================================

-- 用户表
CREATE TABLE IF NOT EXISTS sys_user (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    nickname VARCHAR(100),
    status VARCHAR(20) DEFAULT 'ACTIVE',
    last_login_time TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE sys_user IS '系统用户表';
COMMENT ON COLUMN sys_user.id IS '用户ID';
COMMENT ON COLUMN sys_user.username IS '用户名';
COMMENT ON COLUMN sys_user.password_hash IS '密码哈希值（BCrypt）';
COMMENT ON COLUMN sys_user.nickname IS '用户昵称';
COMMENT ON COLUMN sys_user.status IS '用户状态：ACTIVE-正常, DISABLED-禁用';
COMMENT ON COLUMN sys_user.last_login_time IS '最后登录时间';
COMMENT ON COLUMN sys_user.created_at IS '创建时间';
COMMENT ON COLUMN sys_user.updated_at IS '更新时间';

CREATE INDEX IF NOT EXISTS idx_sys_user_username ON sys_user(username);
CREATE INDEX IF NOT EXISTS idx_sys_user_status ON sys_user(status);

-- 角色表
CREATE TABLE IF NOT EXISTS sys_role (
    id BIGSERIAL PRIMARY KEY,
    role_code VARCHAR(50) NOT NULL UNIQUE,
    role_name VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE sys_role IS '系统角色表';
COMMENT ON COLUMN sys_role.id IS '角色ID';
COMMENT ON COLUMN sys_role.role_code IS '角色编码：ADMIN, USER';
COMMENT ON COLUMN sys_role.role_name IS '角色名称';
COMMENT ON COLUMN sys_role.description IS '角色描述';
COMMENT ON COLUMN sys_role.created_at IS '创建时间';
COMMENT ON COLUMN sys_role.updated_at IS '更新时间';

-- 用户角色关联表
CREATE TABLE IF NOT EXISTS sys_user_role (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_role_user FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_role_role FOREIGN KEY (role_id) REFERENCES sys_role(id) ON DELETE CASCADE,
    UNIQUE (user_id, role_id)
);

COMMENT ON TABLE sys_user_role IS '用户角色关联表';
COMMENT ON COLUMN sys_user_role.id IS '关联ID';
COMMENT ON COLUMN sys_user_role.user_id IS '用户ID';
COMMENT ON COLUMN sys_user_role.role_id IS '角色ID';
COMMENT ON COLUMN sys_user_role.created_at IS '创建时间';

CREATE INDEX IF NOT EXISTS idx_user_role_user ON sys_user_role(user_id);
CREATE INDEX IF NOT EXISTS idx_user_role_role ON sys_user_role(role_id);

-- 文档权限表
CREATE TABLE IF NOT EXISTS doc_permission (
    id BIGSERIAL PRIMARY KEY,
    document_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    can_read BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_doc_perm_doc FOREIGN KEY (document_id) REFERENCES knowledge_document(id) ON DELETE CASCADE,
    CONSTRAINT fk_doc_perm_role FOREIGN KEY (role_id) REFERENCES sys_role(id) ON DELETE CASCADE,
    UNIQUE (document_id, role_id)
);

COMMENT ON TABLE doc_permission IS '文档权限表';
COMMENT ON COLUMN doc_permission.id IS '权限ID';
COMMENT ON COLUMN doc_permission.document_id IS '文档ID';
COMMENT ON COLUMN doc_permission.role_id IS '角色ID';
COMMENT ON COLUMN doc_permission.can_read IS '是否可读';
COMMENT ON COLUMN doc_permission.created_at IS '创建时间';
COMMENT ON COLUMN doc_permission.updated_at IS '更新时间';

CREATE INDEX IF NOT EXISTS idx_doc_perm_doc ON doc_permission(document_id);
CREATE INDEX IF NOT EXISTS idx_doc_perm_role ON doc_permission(role_id);

-- 刷新令牌表
CREATE TABLE IF NOT EXISTS refresh_token (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_refresh_token_user FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE
);

COMMENT ON TABLE refresh_token IS '刷新令牌表';
COMMENT ON COLUMN refresh_token.id IS '令牌ID';
COMMENT ON COLUMN refresh_token.user_id IS '用户ID';
COMMENT ON COLUMN refresh_token.token_hash IS '令牌哈希值';
COMMENT ON COLUMN refresh_token.expires_at IS '过期时间';
COMMENT ON COLUMN refresh_token.created_at IS '创建时间';

CREATE INDEX IF NOT EXISTS idx_refresh_token_user ON refresh_token(user_id);
CREATE INDEX IF NOT EXISTS idx_refresh_token_hash ON refresh_token(token_hash);
CREATE INDEX IF NOT EXISTS idx_refresh_token_expires ON refresh_token(expires_at);

-- =====================================================
-- 初始化数据
-- =====================================================

-- 插入默认角色
INSERT INTO sys_role (role_code, role_name, description) VALUES
('ADMIN', '管理员', '系统管理员，拥有所有权限'),
('USER', '普通用户', '普通用户，只能进行问答和查看历史');

-- 插入初始管理员账号
-- 密码: admin123
-- BCrypt 哈希值: $2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJ8d6qI0
INSERT INTO sys_user (username, password_hash, nickname, status) VALUES
('admin', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJ8d6qI0', '系统管理员', 'ACTIVE');

-- 关联管理员角色
INSERT INTO sys_user_role (user_id, role_id) VALUES (1, 1);