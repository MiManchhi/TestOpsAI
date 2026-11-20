ALTER TABLE users
    MODIFY COLUMN username VARCHAR(160) NOT NULL,
    ADD COLUMN display_name VARCHAR(160) NULL,
    ADD COLUMN enabled BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN revision BIGINT NOT NULL DEFAULT 1,
    ADD COLUMN credential_revision BIGINT NOT NULL DEFAULT 1;

CREATE TABLE workspace_command_receipt (
    actor_id BIGINT NOT NULL,
    operation VARCHAR(48) NOT NULL,
    target_id BIGINT NOT NULL,
    command_key BINARY(16) NOT NULL,
    request_digest BINARY(32) NOT NULL,
    response_json JSON NULL,
    expires_at DATETIME(6) NOT NULL,
    PRIMARY KEY (actor_id, operation, target_id, command_key),
    INDEX workspace_receipt_expiry (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE workspace_audit_event (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    actor_id BIGINT NOT NULL,
    operation VARCHAR(48) NOT NULL,
    target_id BIGINT NOT NULL,
    request_id VARCHAR(64) NULL,
    occurred_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
