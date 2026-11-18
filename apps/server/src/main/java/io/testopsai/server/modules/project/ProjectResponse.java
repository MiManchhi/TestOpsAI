package io.testopsai.server.modules.project;

import java.time.Instant;

public record ProjectResponse(
    String id, String key, String name, String description, String role, Instant createdAt) {
  public static ProjectResponse from(Project p, String role) {
    return new ProjectResponse(
        String.valueOf(p.id()), p.projectKey(), p.name(), p.description(), role, p.createdAt());
  }
}
