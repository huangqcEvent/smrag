-- =====================================================
-- 修复管理员密码哈希值
-- =====================================================

-- 更新管理员密码为正确的 BCrypt 哈希值
-- 密码: admin123
-- 使用 Python bcrypt 生成（兼容 Spring Security BCryptPasswordEncoder）
-- 注意：BCrypt hash 总长度为 60 字符，原来的哈希值只有 59 字符，缺少最后一个字符
UPDATE sys_user
SET password_hash = '$2b$12$eQc.eMzJnLdBb22x9qTHkeq3O37UbofHu0kBwDaY6motkqhxGAA4C'
WHERE username = 'admin';