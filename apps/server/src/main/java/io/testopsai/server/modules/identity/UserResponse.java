package io.testopsai.server.modules.identity;

import java.time.Instant;
import java.util.List;

record UserResponse(Long id, String username, List<String> roles, Instant createdAt) {}
