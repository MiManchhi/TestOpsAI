package io.testopsai.server.modules.catalog;

public class SuiteNotFoundException extends RuntimeException {
  public SuiteNotFoundException(String id) {
    super("Suite not found: " + id);
  }
}
