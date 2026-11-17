package io.testopsai.server.modules.testcase;

public class InvalidSortFieldException extends RuntimeException {
  public InvalidSortFieldException(String field) {
    super("Invalid sort field: " + field);
  }
}
