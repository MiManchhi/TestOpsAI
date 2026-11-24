CREATE TABLE workspace_run (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    provided_by BIGINT NOT NULL,
    name VARCHAR(160) NOT NULL,
    executed_at DATETIME(6) NOT NULL,
    job_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL UNIQUE,
    output_object_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    parse_status VARCHAR(12) NOT NULL CHECK (parse_status IN ('QUEUED','PROCESSING','READY','FAILED')),
    revision BIGINT NOT NULL DEFAULT 1,
    total INT NOT NULL DEFAULT 0,
    passed INT NOT NULL DEFAULT 0,
    failed INT NOT NULL DEFAULT 0,
    skipped INT NOT NULL DEFAULT 0,
    error_code VARCHAR(64) NULL,
    started_at DATETIME(6) NULL,
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    INDEX workspace_run_list (project_id,executed_at,id),
    INDEX workspace_run_queue (parse_status,updated_at,id),
    FOREIGN KEY (project_id) REFERENCES projects(id),
    FOREIGN KEY (provided_by) REFERENCES users(id),
    FOREIGN KEY (output_object_id) REFERENCES workspace_attachment(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE workspace_run_artifact (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    run_id BIGINT NOT NULL,
    attachment_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    kind VARCHAR(12) NOT NULL CHECK (kind IN ('OUTPUT_XML','LOG_TEXT')),
    UNIQUE KEY workspace_run_artifact_file (run_id,attachment_id),
    FOREIGN KEY (run_id) REFERENCES workspace_run(id),
    FOREIGN KEY (attachment_id) REFERENCES workspace_attachment(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE workspace_run_result (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    run_id BIGINT NOT NULL,
    artifact_id BIGINT NOT NULL,
    ordinal INT NOT NULL,
    case_name VARCHAR(160) NOT NULL,
    suite VARCHAR(512) NOT NULL,
    robot_status VARCHAR(4) NOT NULL CHECK (robot_status IN ('PASS','FAIL','SKIP')),
    UNIQUE KEY workspace_run_result_order (run_id,ordinal),
    FOREIGN KEY (run_id) REFERENCES workspace_run(id),
    FOREIGN KEY (artifact_id) REFERENCES workspace_run_artifact(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE workspace_chat_turn ADD COLUMN run_id BIGINT NULL,
    ADD FOREIGN KEY (run_id) REFERENCES workspace_run(id);
