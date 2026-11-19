package io.testopsai.server.modules.catalog;

public class CaseNotFoundException extends RuntimeException {
  public CaseNotFoundException(String id) {
    super("Test case not found: " + id);
  }
}
