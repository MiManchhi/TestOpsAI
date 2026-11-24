package io.testopsai.server.platform.security;

import io.testopsai.server.modules.identity.AppUser;
import io.testopsai.server.modules.identity.UserRepository;
import io.testopsai.server.platform.web.WorkspaceFailure;
import org.springframework.http.HttpStatus;
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
        .filter(
            user ->
                user.enabled()
                    && (!(authentication.getDetails() instanceof Long revision)
                        || revision == user.credentialRevision()))
        .orElseThrow(
            () ->
                new WorkspaceFailure(
                    HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentication required"));
  }
}
