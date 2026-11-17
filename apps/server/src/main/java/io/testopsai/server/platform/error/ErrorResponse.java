package io.testopsai.server.platform.error;

public record ErrorResponse(String code, String message, String requestId) {}
