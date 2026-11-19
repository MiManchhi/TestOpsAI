package io.testopsai.server.platform.security;

import io.testopsai.server.modules.identity.AppUser;
import io.testopsai.server.modules.identity.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/** Resolves the authenticated principal into the persisted {@link AppUser}. */
@Component
public class CurrentUserResolver {
  private final UserRepository userRepository;

  public CurrentUserResolver(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  public AppUser resolve(Authentication authentication) {
    return userRepository
        .findByUsername(authentication.getName())
        .orElseThrow(() -> new IllegalStateException("Authenticated user not found in database"));
  }
}
