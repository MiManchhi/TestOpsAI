ALTER TABLE project_members
    DROP CHECK chk_pm_role,
    ADD CONSTRAINT chk_pm_role CHECK (role IN ('OWNER', 'EDITOR', 'VIEWER', 'TEST', 'DEV')),
    ADD COLUMN revision BIGINT NOT NULL DEFAULT 1,
    ADD COLUMN owner_project_id BIGINT GENERATED ALWAYS AS
        (CASE WHEN role = 'OWNER' THEN project_id ELSE NULL END) VIRTUAL,
    ADD UNIQUE KEY workspace_one_owner (owner_project_id);

ALTER TABLE projects
    MODIFY COLUMN name VARCHAR(160) NOT NULL,
    MODIFY COLUMN description TEXT NULL,
    ADD COLUMN revision BIGINT NOT NULL DEFAULT 1,
    ADD COLUMN auth_revision BIGINT NOT NULL DEFAULT 1;
