-- M1.3 Business metadata: knowledge bases and test cases
-- Scope: knowledge_bases, kb_members, test_cases + demo seed data
-- Conventions: BIGINT auto-increment PK, DATETIME(6) UTC, snake_case, utf8mb4_unicode_ci

CREATE TABLE knowledge_bases (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(512) NULL,
    owner_id BIGINT NOT NULL,
    visibility VARCHAR(16) NOT NULL DEFAULT 'PRIVATE',
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_knowledge_bases PRIMARY KEY (id),
    CONSTRAINT fk_kb_owner FOREIGN KEY (owner_id) REFERENCES users(id),
    CONSTRAINT uq_kb_owner_name UNIQUE (owner_id, name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE kb_members (
    kb_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    access_level VARCHAR(16) NOT NULL DEFAULT 'READ',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_kb_members PRIMARY KEY (kb_id, user_id),
    CONSTRAINT fk_kb_members_kb FOREIGN KEY (kb_id) REFERENCES knowledge_bases(id) ON DELETE CASCADE,
    CONSTRAINT fk_kb_members_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE test_cases (
    id BIGINT NOT NULL AUTO_INCREMENT,
    project_key VARCHAR(64) NOT NULL,
    module VARCHAR(128) NOT NULL,
    title VARCHAR(256) NOT NULL,
    tags VARCHAR(512) NULL,
    priority VARCHAR(16) NOT NULL DEFAULT 'P2',
    expected_result TEXT NULL,
    owner_id BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_test_cases PRIMARY KEY (id),
    CONSTRAINT fk_tc_owner FOREIGN KEY (owner_id) REFERENCES users(id),
    INDEX idx_tc_project_module (project_key, module, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Seed demo knowledge bases (bound to admin user id=1)
INSERT INTO knowledge_bases (name, description, owner_id, visibility, status) VALUES
    ('Platform Docs', 'Core platform documentation and onboarding guides', 1, 'PUBLIC', 'ACTIVE'),
    ('QA Playbook', 'Internal QA team testing strategies and checklists', 1, 'SHARED', 'ACTIVE'),
    ('Personal Notes', 'Private research notes and drafts', 1, 'PRIVATE', 'ACTIVE');

-- Seed demo test cases (bound to admin user id=1)
INSERT INTO test_cases (project_key, module, title, tags, priority, expected_result, owner_id, status) VALUES
    ('TESTOPS', 'auth', 'Login with valid credentials returns JWT', 'auth,login,happy-path', 'P0', '200 with accessToken and expiresIn fields', 1, 'ACTIVE'),
    ('TESTOPS', 'auth', 'Login with wrong password returns 401', 'auth,login,negative', 'P0', '401 INVALID_CREDENTIALS structured error', 1, 'ACTIVE'),
    ('TESTOPS', 'auth', 'Access protected endpoint without token returns 401', 'auth,security', 'P1', '401 UNAUTHORIZED with requestId', 1, 'ACTIVE'),
    ('TESTOPS', 'knowledge', 'Create knowledge base returns 201', 'kb,crud,happy-path', 'P1', '201 with id, name, visibility fields', 1, 'ACTIVE'),
    ('TESTOPS', 'knowledge', 'Non-owner cannot read private KB', 'kb,permission,negative', 'P0', '403 FORBIDDEN', 1, 'ACTIVE'),
    ('TESTOPS', 'testcase', 'Filter test cases by project key', 'tc,filter,pagination', 'P2', 'Only matching project_key returned', 1, 'ACTIVE'),
    ('TESTOPS', 'testcase', 'Sort by whitelisted field ascending', 'tc,sort,pagination', 'P2', 'Results ordered by specified field ASC', 1, 'ACTIVE'),
    ('DEMO', 'smoke', 'Health endpoint returns UP', 'infra,smoke', 'P3', '200 {"status":"UP"}', 1, 'ACTIVE');
