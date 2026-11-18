package io.testopsai.server.modules.project;

import java.time.Instant;

record Project(Long id, String projectKey, String name, String description, Instant createdAt) {}
