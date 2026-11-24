CREATE TABLE workspace_owner_transfer (
    handover_id BINARY(16) PRIMARY KEY,
    project_id BIGINT NOT NULL,
    old_owner_id BIGINT NOT NULL,
    new_owner_id BIGINT NOT NULL,
    former_role VARCHAR(16) NOT NULL CHECK (former_role IN ('TEST','DEV')),
    mode VARCHAR(20) NOT NULL CHECK (mode IN ('OWNER_REQUEST','ADMIN_PROXY')),
    status VARCHAR(16) NOT NULL CHECK (status IN ('PENDING','APPROVED','RETURNED')),
    requested_by BIGINT NOT NULL,
    decided_by BIGINT NULL,
    reason TEXT NOT NULL,
    revision BIGINT NOT NULL DEFAULT 1,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    pending_project_id BIGINT GENERATED ALWAYS AS
        (CASE WHEN status = 'PENDING' THEN project_id ELSE NULL END) STORED,
    UNIQUE KEY workspace_one_pending_transfer (pending_project_id),
    KEY workspace_transfer_history (project_id,created_at,handover_id),
    CONSTRAINT workspace_transfer_project FOREIGN KEY (project_id) REFERENCES projects(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
