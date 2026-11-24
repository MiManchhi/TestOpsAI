CREATE TABLE workspace_attachment (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    project_id BIGINT NOT NULL,
    uploaded_by BIGINT NOT NULL,
    file_name VARCHAR(160) NOT NULL,
    sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    size_bytes BIGINT NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    INDEX workspace_attachment_expiry (expires_at),
    CONSTRAINT workspace_attachment_project FOREIGN KEY (project_id) REFERENCES projects(id),
    CONSTRAINT workspace_attachment_user FOREIGN KEY (uploaded_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE workspace_document (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    title VARCHAR(160) NOT NULL,
    kind VARCHAR(12) NOT NULL CHECK (kind IN ('TENDER','SPEC','KEYWORDS')),
    visibility VARCHAR(12) NOT NULL DEFAULT 'PROJECT' CHECK (visibility IN ('PROJECT','RESTRICTED')),
    revision BIGINT NOT NULL DEFAULT 1,
    UNIQUE KEY workspace_doc_scope (project_id,id),
    CONSTRAINT workspace_doc_project FOREIGN KEY (project_id) REFERENCES projects(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE workspace_doc_reader (
    project_id BIGINT NOT NULL,
    document_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    grant_kind VARCHAR(12) NOT NULL CHECK (grant_kind IN ('EXPLICIT','REVIEWER')),
    PRIMARY KEY (project_id,document_id,user_id,grant_kind),
    CONSTRAINT workspace_doc_reader_document FOREIGN KEY (project_id,document_id)
        REFERENCES workspace_document(project_id,id),
    CONSTRAINT workspace_doc_reader_member FOREIGN KEY (project_id,user_id)
        REFERENCES project_members(project_id,user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE workspace_doc_version (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    document_id BIGINT NOT NULL,
    attachment_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    version_label VARCHAR(160) NOT NULL,
    submitted_by BIGINT NOT NULL,
    review_job_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL UNIQUE,
    reviewed_at DATETIME(6) NULL,
    change_note TEXT NOT NULL,
    review_status VARCHAR(12) NOT NULL DEFAULT 'PENDING',
    index_status VARCHAR(12) NOT NULL DEFAULT 'NOT_STARTED',
    revision BIGINT NOT NULL DEFAULT 1,
    reviewed_by BIGINT NULL,
    reason TEXT NOT NULL,
    error_code VARCHAR(64) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY workspace_doc_version_label (document_id,version_label),
    UNIQUE KEY workspace_version_attachment (attachment_id),
    CONSTRAINT workspace_version_document FOREIGN KEY (document_id) REFERENCES workspace_document(id),
    CONSTRAINT workspace_version_attachment_fk FOREIGN KEY (attachment_id) REFERENCES workspace_attachment(id),
    CONSTRAINT workspace_version_submitter FOREIGN KEY (submitted_by) REFERENCES users(id),
    CONSTRAINT workspace_version_state CHECK (
        (review_status IN ('PENDING','RETURNED') AND index_status = 'NOT_STARTED')
        OR (review_status = 'APPROVED' AND index_status IN ('QUEUED','PROCESSING','READY','FAILED'))
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE workspace_document_block (
    version_id BIGINT NOT NULL,
    ordinal INT NOT NULL,
    locator VARCHAR(96) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    content TEXT NOT NULL,
    PRIMARY KEY (version_id,ordinal),
    UNIQUE KEY workspace_document_locator (version_id,locator),
    CONSTRAINT workspace_block_version FOREIGN KEY (version_id) REFERENCES workspace_doc_version(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE workspace_index_job (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    project_id BIGINT NOT NULL,
    version_id BIGINT NOT NULL,
    actor_id BIGINT NOT NULL,
    stage VARCHAR(16) NOT NULL DEFAULT 'QUEUED',
    status VARCHAR(16) NOT NULL DEFAULT 'QUEUED',
    ai_job_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    error_code VARCHAR(64) NULL,
    failure_count INT NOT NULL DEFAULT 0,
    lease_token CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    lease_until DATETIME(6) NULL,
    next_attempt_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    INDEX workspace_index_due (status,next_attempt_at,lease_until),
    INDEX workspace_index_project (project_id,created_at,id),
    CONSTRAINT workspace_index_version FOREIGN KEY (version_id) REFERENCES workspace_doc_version(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE workspace_basis (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    label VARCHAR(160) NOT NULL,
    revision BIGINT NOT NULL,
    published_by BIGINT NOT NULL,
    effective_from DATETIME(6) NOT NULL,
    effective_to DATETIME(6) NULL,
    active_project_id BIGINT GENERATED ALWAYS AS
        (CASE WHEN effective_to IS NULL THEN project_id ELSE NULL END) VIRTUAL,
    UNIQUE KEY workspace_one_active_basis (active_project_id),
    UNIQUE KEY workspace_basis_revision (project_id,revision),
    UNIQUE KEY workspace_basis_label (project_id,label),
    CONSTRAINT workspace_basis_project FOREIGN KEY (project_id) REFERENCES projects(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE workspace_basis_version (
    basis_id BIGINT NOT NULL,
    ordinal SMALLINT NOT NULL,
    version_id BIGINT NOT NULL,
    PRIMARY KEY (basis_id,ordinal),
    UNIQUE KEY workspace_basis_unique_version (basis_id,version_id),
    CONSTRAINT workspace_basis_set FOREIGN KEY (basis_id) REFERENCES workspace_basis(id),
    CONSTRAINT workspace_basis_version_fk FOREIGN KEY (version_id) REFERENCES workspace_doc_version(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
