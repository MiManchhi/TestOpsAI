package io.testopsai.server.modules.project;

public class NotProjectMemberException extends RuntimeException {
  public NotProjectMemberException(Long userId, Long projectId) {
    super("User " + userId + " is not a member of project " + projectId);
  }
}
