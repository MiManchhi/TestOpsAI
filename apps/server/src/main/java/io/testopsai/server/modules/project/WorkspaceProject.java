package io.testopsai.server.modules.project;

public record WorkspaceProject(
    String projectId, String name, String description, Person owner, long revision, String myRole) {
  public record Person(String userId, String displayName) {}

  public record Member(String userId, String displayName, String role, long revision) {}
}
