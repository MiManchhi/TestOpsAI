-- M1.2 Seed roles and demo admin
-- Roles: ADMIN, USER
-- Demo admin: username=admin, password=admin123 (bcrypt hash below)
-- WARNING: This is for local development only. Change the password in production.

INSERT INTO roles (role_code, description) VALUES
    ('ADMIN', 'Platform administrator'),
    ('USER', 'Standard user');

-- bcrypt hash of 'admin123' (cost 10)
INSERT INTO users (username, password_hash) VALUES
    ('admin', '$2a$10$EqKcp1WFKAr3Bo4MHPbBHe5xFJhGPMBGPMFYMGBnHFMGfnGMFnGMe');

INSERT INTO user_roles (user_id, role_id)
    SELECT u.id, r.id FROM users u, roles r WHERE u.username = 'admin' AND r.role_code = 'ADMIN';
