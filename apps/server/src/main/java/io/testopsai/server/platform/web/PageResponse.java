package io.testopsai.server.platform.web;

import java.util.List;

public record PageResponse<T>(List<T> items, long total, int page, int size) {
  public static void validatePagination(int page, int size) {
    if (page < 0) throw new IllegalArgumentException("page must be >= 0");
    if (size < 1 || size > 100)
      throw new IllegalArgumentException("size must be between 1 and 100");
  }
}
