-- Fix seed data owner_id: V5 hardcoded owner_id=1 assuming admin is first user.
-- On databases where test data shifted auto-increment, admin may have a different id.
-- This migration rebinds seed knowledge_bases and test_cases to the actual admin user.

UPDATE knowledge_bases SET owner_id = (SELECT id FROM users WHERE username = 'admin')
    WHERE owner_id = 1 AND NOT EXISTS (SELECT 1 FROM users WHERE id = 1 AND username = 'admin');

UPDATE test_cases SET owner_id = (SELECT id FROM users WHERE username = 'admin')
    WHERE owner_id = 1 AND NOT EXISTS (SELECT 1 FROM users WHERE id = 1 AND username = 'admin');
