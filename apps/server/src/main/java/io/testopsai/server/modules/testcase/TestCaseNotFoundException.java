package io.testopsai.server.modules.testcase;

public class TestCaseNotFoundException extends RuntimeException {
  public TestCaseNotFoundException(Long id) {
    super("Test case not found: " + id);
  }
}
