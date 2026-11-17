-- Fix admin password hash to match 'admin123'
-- The V2 hash was a placeholder; this replaces it with a verified bcrypt hash.
UPDATE users SET password_hash = '$2a$10$dkCIbrg0uM8pHxxfBg0en.Yv0E79tOW9tB.MXEwWKuIJUS0PZyuSy' WHERE username = 'admin';
