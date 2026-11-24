CREATE TABLE workspace_chat (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    project_id BIGINT NOT NULL,
    actor_id BIGINT NOT NULL,
    title VARCHAR(160) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    INDEX workspace_chat_history (project_id,actor_id,updated_at,id),
    FOREIGN KEY (project_id) REFERENCES projects(id),
    FOREIGN KEY (actor_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE workspace_chat_turn (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    conversation_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    client_message_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    message TEXT NOT NULL,
    status VARCHAR(12) NOT NULL CHECK (status IN ('QUEUED','RUNNING','COMPLETED','FAILED','CANCELLED')),
    attachments JSON NOT NULL,
    answer JSON NULL,
    authorization_revision BIGINT NULL,
    authorized_versions JSON NULL,
    request_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    started_at DATETIME(6) NULL,
    finished_at DATETIME(6) NULL,
    UNIQUE KEY workspace_chat_message (conversation_id,client_message_id),
    INDEX workspace_chat_work (status,created_at,id),
    FOREIGN KEY (conversation_id) REFERENCES workspace_chat(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE workspace_chat_turn_file (
    turn_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    attachment_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    PRIMARY KEY (turn_id,attachment_id),
    FOREIGN KEY (turn_id) REFERENCES workspace_chat_turn(id),
    FOREIGN KEY (attachment_id) REFERENCES workspace_attachment(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE workspace_chat_citation (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    turn_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    source JSON NOT NULL,
    FOREIGN KEY (turn_id) REFERENCES workspace_chat_turn(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
