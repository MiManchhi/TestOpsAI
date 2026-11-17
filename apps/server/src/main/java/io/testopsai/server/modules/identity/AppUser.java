package io.testopsai.server.modules.identity;

import java.time.Instant;
import java.util.List;

public record AppUser(
    Long id, String username, String passwordHash, List<String> roles, Instant createdAt) {}
