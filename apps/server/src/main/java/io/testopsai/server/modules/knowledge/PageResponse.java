package io.testopsai.server.modules.knowledge;

import java.util.List;

public record PageResponse<T>(List<T> items, long total, int page, int size) {}
