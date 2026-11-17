package io.testopsai.server.modules.identity;

record LoginResponse(String accessToken, long expiresIn) {}
