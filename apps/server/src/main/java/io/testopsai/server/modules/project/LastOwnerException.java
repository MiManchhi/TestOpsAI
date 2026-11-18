package io.testopsai.server.modules.project;

public class LastOwnerException extends RuntimeException {
  public LastOwnerException(Long projectId) {
    super("Cannot remove the last owner of project " + projectId);
  }
}
