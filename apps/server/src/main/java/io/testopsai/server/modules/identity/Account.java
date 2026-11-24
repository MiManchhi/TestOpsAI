package io.testopsai.server.modules.identity;

public record Account(
    String userId,
    String username,
    String displayName,
    boolean enabled,
    boolean mustChangePassword,
    long revision) {
  static Account from(AppUser user) {
    return new Account(
        user.id().toString(),
        user.username(),
        user.displayName(),
        user.enabled(),
        user.mustChangePassword(),
        user.revision());
  }
}
