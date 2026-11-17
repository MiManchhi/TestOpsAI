package io.testopsai.server.modules.identity;

import io.testopsai.server.platform.error.ErrorResponse;
import io.testopsai.server.platform.error.RequestIdFilter;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
class AuthController {
  private final AuthService authService;

  AuthController(AuthService authService) {
    this.authService = authService;
  }

  @PostMapping("/login")
  ResponseEntity<?> login(@RequestBody LoginRequest request) {
    LoginResponse response = authService.login(request);
    if (response == null) {
      String requestId = MDC.get(RequestIdFilter.MDC_KEY);
      if (requestId == null) requestId = "unknown";
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
          .body(
              new ErrorResponse("INVALID_CREDENTIALS", "Invalid username or password", requestId));
    }
    return ResponseEntity.ok(response);
  }
}
