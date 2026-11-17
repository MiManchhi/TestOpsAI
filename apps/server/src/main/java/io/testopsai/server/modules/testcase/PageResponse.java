package io.testopsai.server.modules.testcase;

import java.util.List;

record PageResponse<T>(List<T> items, long total, int page, int size) {}
