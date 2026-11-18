-- M1.5 Project isolation baseline (contract 1.0.0)
-- Scope: projects, project_members. Foundation for all /api/v2 business operations.
-- Conventions: BIGINT auto-increment PK, DATETIME(6) UTC, snake_case, utf8mb4_unicode_ci

CREATE TABLE projects (
    id BIGINT NOT NULL AUTO_INCREMENT,
    project_key VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(1000) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_projects PRIMARY KEY (id),
    CONSTRAINT uq_projects_key UNIQUE (project_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE project_members (
    project_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role VARCHAR(16) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_project_members PRIMARY KEY (project_id, user_id),
    CONSTRAINT fk_pm_project FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE,
    CONSTRAINT fk_pm_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT chk_pm_role CHECK (role IN ('OWNER', 'EDITOR', 'VIEWER'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Seed a default project for existing admin user (id=1)
INSERT INTO projects (project_key, name, description) VALUES
    ('DEMO', 'Demo Project', 'Default project seeded for development');

INSERT INTO project_members (project_id, user_id, role)
    SELECT p.id, u.id, 'OWNER' FROM projects p, users u WHERE p.project_key = 'DEMO' AND u.username = 'admin';
