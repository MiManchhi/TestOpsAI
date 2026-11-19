-- M1.6 Project case catalog: Robot suites, project-scoped test cases, tag index, selection snapshots.
-- Contract 1.0.0: /api/v2/projects/{projectId}/test-suites, /test-cases, /case-selections.
-- v1 tables (test_cases with owner_id/comma tags) keep their existing semantics; v2 uses new tables.
-- Conventions: BIGINT auto-increment PK, DATETIME(6) UTC, snake_case, utf8mb4_unicode_ci.

CREATE TABLE suites (
    id BIGINT NOT NULL AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    parent_id BIGINT NULL,
    name VARCHAR(256) NOT NULL,
    source_path VARCHAR(512) NOT NULL,
    revision CHAR(64) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_suites PRIMARY KEY (id),
    CONSTRAINT fk_suites_project FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE,
    CONSTRAINT fk_suites_parent FOREIGN KEY (parent_id) REFERENCES suites(id) ON DELETE CASCADE,
    CONSTRAINT uq_suites_path_revision UNIQUE (project_id, source_path, revision),
    INDEX idx_suites_project_parent (project_id, parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE project_test_cases (
    id BIGINT NOT NULL AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    suite_id BIGINT NULL,
    stable_key VARCHAR(512) NOT NULL,
    title VARCHAR(256) NOT NULL,
    module VARCHAR(128) NOT NULL,
    priority VARCHAR(16) NOT NULL DEFAULT 'P2',
    lifecycle_status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    source_type VARCHAR(16) NOT NULL,
    documentation TEXT NULL,
    expected_result TEXT NULL,
    source_path VARCHAR(512) NULL,
    source_line INT NULL,
    source_revision CHAR(64) NULL,
    steps_json TEXT NULL,
    version INT NOT NULL DEFAULT 1,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_project_test_cases PRIMARY KEY (id),
    CONSTRAINT fk_ptc_project FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE,
    CONSTRAINT fk_ptc_suite FOREIGN KEY (suite_id) REFERENCES suites(id) ON DELETE SET NULL,
    CONSTRAINT uq_ptc_project_stable_key UNIQUE (project_id, stable_key),
    INDEX idx_ptc_project_module (project_id, module),
    INDEX idx_ptc_project_lifecycle (project_id, lifecycle_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE project_case_tags (
    case_id BIGINT NOT NULL,
    tag VARCHAR(64) NOT NULL,
    CONSTRAINT pk_project_case_tags PRIMARY KEY (case_id, tag),
    CONSTRAINT fk_pct_case FOREIGN KEY (case_id) REFERENCES project_test_cases(id) ON DELETE CASCADE,
    INDEX idx_pct_tag (tag)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE case_selections (
    id CHAR(36) NOT NULL,
    project_id BIGINT NOT NULL,
    created_by BIGINT NOT NULL,
    selection_mode VARCHAR(16) NOT NULL,
    case_count INT NOT NULL,
    digest CHAR(64) NOT NULL,
    filter_json TEXT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    expires_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_case_selections PRIMARY KEY (id),
    CONSTRAINT fk_cs_project FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE,
    CONSTRAINT fk_cs_user FOREIGN KEY (created_by) REFERENCES users(id),
    INDEX idx_cs_project_expires (project_id, expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE selection_items (
    selection_id CHAR(36) NOT NULL,
    case_id BIGINT NOT NULL,
    case_version INT NOT NULL,
    CONSTRAINT pk_selection_items PRIMARY KEY (selection_id, case_id),
    CONSTRAINT fk_si_selection FOREIGN KEY (selection_id) REFERENCES case_selections(id) ON DELETE CASCADE,
    CONSTRAINT fk_si_case FOREIGN KEY (case_id) REFERENCES project_test_cases(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Synthetic demo catalog for the seeded DEMO project (self-made data, mirrors docs/contracts/examples.json).
-- Robot import (later slice) will replace this with parsed snapshots.
INSERT INTO suites (project_id, parent_id, name, source_path, revision)
SELECT id, NULL, '费率与时钟', 'suites/tariff.robot', REPEAT('a', 64) FROM projects WHERE project_key = 'DEMO';
INSERT INTO suites (project_id, parent_id, name, source_path, revision)
SELECT id, NULL, '通信与事件', 'suites/protocol.robot', REPEAT('a', 64) FROM projects WHERE project_key = 'DEMO';

-- Six synthetic ROBOT cases mirroring contract examples (ids differ; titles/tags match).
INSERT INTO project_test_cases
    (project_id, suite_id, stable_key, title, module, priority, lifecycle_status, source_type,
     documentation, expected_result, source_path, source_line, source_revision, steps_json, version)
SELECT p.id, s.id, 'ed978316db6d2a6578334e65d271e68ab281a00e76dcd2203b4d0c709224e942',
       '费率切换前状态核对', '费率与时钟', 'P1', 'ACTIVE', 'ROBOT',
       '自造测试定义；用于验证对应模拟场景。', '模拟状态与示例规范中的对应规则一致',
       'suites/tariff.robot', 14, REPEAT('a', 64),
       '[{"keyword":"Read Demo Meter State","arguments":["tariff"],"line":15},{"keyword":"Check Demo State","arguments":["expected"],"line":16}]', 1
FROM projects p JOIN suites s ON s.project_id = p.id AND s.source_path = 'suites/tariff.robot'
WHERE p.project_key = 'DEMO';

INSERT INTO project_test_cases
    (project_id, suite_id, stable_key, title, module, priority, lifecycle_status, source_type,
     documentation, expected_result, source_path, source_line, source_revision, steps_json, version)
SELECT p.id, s.id, '541c097e1843c4e66fafd417147879aa3e316f8cedeb0445e89ab3ba83863596',
       '费率切换后状态核对', '费率与时钟', 'P1', 'ACTIVE', 'ROBOT',
       '自造测试定义；用于验证对应模拟场景。', '模拟状态与示例规范中的对应规则一致',
       'suites/tariff.robot', 18, REPEAT('a', 64),
       '[{"keyword":"Read Demo Meter State","arguments":["tariff"],"line":19},{"keyword":"Check Demo State","arguments":["expected"],"line":20}]', 1
FROM projects p JOIN suites s ON s.project_id = p.id AND s.source_path = 'suites/tariff.robot'
WHERE p.project_key = 'DEMO';

INSERT INTO project_test_cases
    (project_id, suite_id, stable_key, title, module, priority, lifecycle_status, source_type,
     documentation, expected_result, source_path, source_line, source_revision, steps_json, version)
SELECT p.id, s.id, 'ef6d12c90517c26ab89f48bf1ea7591dca975ba5dfbb005d0aef99315c467037',
       '通信超时返回处理', '通信与事件', 'P1', 'ACTIVE', 'ROBOT',
       '自造测试定义；用于验证对应模拟场景。', '模拟状态与示例规范中的对应规则一致',
       'suites/protocol.robot', 22, REPEAT('a', 64),
       '[{"keyword":"Read Demo Meter State","arguments":["tariff"],"line":23},{"keyword":"Check Demo State","arguments":["expected"],"line":24}]', 1
FROM projects p JOIN suites s ON s.project_id = p.id AND s.source_path = 'suites/protocol.robot'
WHERE p.project_key = 'DEMO';

INSERT INTO project_test_cases
    (project_id, suite_id, stable_key, title, module, priority, lifecycle_status, source_type,
     documentation, expected_result, source_path, source_line, source_revision, steps_json, version)
SELECT p.id, s.id, 'b8f4ace22e1efca17352c4b4f7d92760b0f0f3351b3ea4821a9fac01fd6cb470',
       '事件记录读取一致性', '通信与事件', 'P2', 'ACTIVE', 'ROBOT',
       '自造测试定义；用于验证对应模拟场景。', '模拟状态与示例规范中的对应规则一致',
       'suites/protocol.robot', 26, REPEAT('a', 64),
       '[{"keyword":"Read Demo Meter State","arguments":["tariff"],"line":27},{"keyword":"Check Demo State","arguments":["expected"],"line":28}]', 1
FROM projects p JOIN suites s ON s.project_id = p.id AND s.source_path = 'suites/protocol.robot'
WHERE p.project_key = 'DEMO';

INSERT INTO project_test_cases
    (project_id, suite_id, stable_key, title, module, priority, lifecycle_status, source_type,
     documentation, expected_result, source_path, source_line, source_revision, steps_json, version)
SELECT p.id, s.id, 'e4b863d58fd79285e7b00486d01063cc59b3d47c6424b26cc4339e08ba349edc',
       '校时后费率切换', '费率与时钟', 'P2', 'ACTIVE', 'ROBOT',
       '自造测试定义；用于验证对应模拟场景。', '模拟状态与示例规范中的对应规则一致',
       'suites/tariff.robot', 30, REPEAT('a', 64),
       '[{"keyword":"Read Demo Meter State","arguments":["tariff"],"line":31},{"keyword":"Check Demo State","arguments":["expected"],"line":32}]', 1
FROM projects p JOIN suites s ON s.project_id = p.id AND s.source_path = 'suites/tariff.robot'
WHERE p.project_key = 'DEMO';

INSERT INTO project_test_cases
    (project_id, suite_id, stable_key, title, module, priority, lifecycle_status, source_type,
     documentation, expected_result, source_path, source_line, source_revision, steps_json, version)
SELECT p.id, s.id, 'cbe65e8e066507c61de209158abe9ac1a783896ab2afd9febc49471165e79fdf',
       '掉电恢复后的费率状态', '费率与时钟', 'P2', 'ACTIVE', 'ROBOT',
       '自造测试定义；用于验证对应模拟场景。', '模拟状态与示例规范中的对应规则一致',
       'suites/tariff.robot', 34, REPEAT('a', 64),
       '[{"keyword":"Read Demo Meter State","arguments":["tariff"],"line":35},{"keyword":"Check Demo State","arguments":["expected"],"line":36}]', 1
FROM projects p JOIN suites s ON s.project_id = p.id AND s.source_path = 'suites/tariff.robot'
WHERE p.project_key = 'DEMO';

-- Tags (exact, case-sensitive, deduplicated) for the seeded cases.
INSERT INTO project_case_tags (case_id, tag)
SELECT c.id, 'smoke' FROM project_test_cases c
WHERE c.stable_key IN ('ed978316db6d2a6578334e65d271e68ab281a00e76dcd2203b4d0c709224e942',
                       '541c097e1843c4e66fafd417147879aa3e316f8cedeb0445e89ab3ba83863596');
INSERT INTO project_case_tags (case_id, tag)
SELECT c.id, 'tariff' FROM project_test_cases c
WHERE c.stable_key IN ('ed978316db6d2a6578334e65d271e68ab281a00e76dcd2203b4d0c709224e942',
                       '541c097e1843c4e66fafd417147879aa3e316f8cedeb0445e89ab3ba83863596');
INSERT INTO project_case_tags (case_id, tag)
SELECT c.id, 'regression' FROM project_test_cases c
WHERE c.stable_key IN ('ef6d12c90517c26ab89f48bf1ea7591dca975ba5dfbb005d0aef99315c467037',
                       'b8f4ace22e1efca17352c4b4f7d92760b0f0f3351b3ea4821a9fac01fd6cb470',
                       'e4b863d58fd79285e7b00486d01063cc59b3d47c6424b26cc4339e08ba349edc',
                       'cbe65e8e066507c61de209158abe9ac1a783896ab2afd9febc49471165e79fdf');
