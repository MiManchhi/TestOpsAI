package io.testopsai.server.modules.identity;

import java.time.Instant;
import java.util.List;

public record AppUser(
    Long id,
    String username,
    String displayName,
    String passwordHash,
    List<String> roles,
    Instant createdAt,
    boolean enabled,
    boolean mustChangePassword,
    long revision,
    long credentialRevision) {}
