package io.testopsai.server.modules.identity;

import io.testopsai.server.platform.security.CurrentUserResolver;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
class UserController {
  private final CurrentUserResolver users;

  UserController(CurrentUserResolver users) {
    this.users = users;
  }

  @GetMapping("/me")
  UserResponse me(Authentication authentication) {
    AppUser user = users.resolve(authentication);
    return new UserResponse(user.id(), user.username(), user.roles(), user.createdAt());
  }
}
