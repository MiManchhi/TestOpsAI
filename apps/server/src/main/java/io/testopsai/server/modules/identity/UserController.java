package io.testopsai.server.modules.identity;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
class UserController {
  private final UserRepository userRepository;

  UserController(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  @GetMapping("/me")
  ResponseEntity<UserResponse> me(Authentication authentication) {
    String username = authentication.getName();
    return userRepository
        .findByUsername(username)
        .map(u -> new UserResponse(u.id(), u.username(), u.roles(), u.createdAt()))
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.notFound().build());
  }
}
