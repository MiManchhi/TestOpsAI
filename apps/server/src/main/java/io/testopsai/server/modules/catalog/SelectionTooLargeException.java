package io.testopsai.server.modules.catalog;

public class SelectionTooLargeException extends RuntimeException {
  public SelectionTooLargeException(long count) {
    super("Selection contains " + count + " cases; the maximum is 1000");
  }
}
