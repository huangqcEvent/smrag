-- The original demo migrations shipped a public admin/admin123 credential.
-- Lock only those exact seeded hashes. A password changed by the user is kept.
UPDATE sys_user
SET password_hash = '!BOOTSTRAP_ADMIN_PASSWORD_REQUIRED!',
    updated_at = CURRENT_TIMESTAMP
WHERE username = 'admin'
  AND password_hash IN (
      '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJ8d6qI0',
      '$2b$12$eQc.eMzJnLdBb22x9qTHkeq3O37UbofHu0kBwDaY6motkqhxGAA4C'
  );
