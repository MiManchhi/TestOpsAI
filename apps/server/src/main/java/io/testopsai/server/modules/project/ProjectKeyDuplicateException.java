package io.testopsai.server.modules.project;

public class ProjectKeyDuplicateException extends RuntimeException {
  public ProjectKeyDuplicateException(String key) {
    super("Project key already exists: " + key);
  }
}
