package io.testopsai.server.modules.project;

import java.time.Instant;

record ProjectMember(Long projectId, Long userId, String role, Instant createdAt) {}
