-- Ensure admin has ADMIN role (V2 INSERT may have failed due to test data interference)
INSERT IGNORE INTO user_roles (user_id, role_id)
    SELECT u.id, r.id FROM users u, roles r WHERE u.username = 'admin' AND r.role_code = 'ADMIN';
