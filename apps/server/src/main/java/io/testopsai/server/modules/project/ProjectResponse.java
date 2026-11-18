package io.testopsai.server.modules.project;

import java.time.Instant;

public record ProjectResponse(
    String id, String key, String name, String description, String role, Instant createdAt) {
  public static ProjectResponse from(Project p, String role) {
    // The frozen contract requires a non-null string; the column is nullable for legacy rows.
    String description = p.description() != null ? p.description() : "";
    return new ProjectResponse(
        String.valueOf(p.id()), p.projectKey(), p.name(), description, role, p.createdAt());
  }
}
