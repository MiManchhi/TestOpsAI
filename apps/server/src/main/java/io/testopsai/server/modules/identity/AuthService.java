package io.testopsai.server.modules.identity;

import io.testopsai.server.platform.security.JwtProperties;
import io.testopsai.server.platform.security.JwtTokenProvider;
import java.util.Optional;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;

@Service
class AuthService {
  private final AuthenticationManager authenticationManager;
  private final JwtTokenProvider tokenProvider;
  private final UserRepository userRepository;
  private final int expirationMinutes;

  AuthService(
      AuthenticationManager authenticationManager,
      JwtTokenProvider tokenProvider,
      UserRepository userRepository,
      JwtProperties jwtProperties) {
    this.authenticationManager = authenticationManager;
    this.tokenProvider = tokenProvider;
    this.userRepository = userRepository;
    this.expirationMinutes = jwtProperties.expirationMinutes();
  }

  LoginResponse login(LoginRequest request) {
    try {
      authenticationManager.authenticate(
          new UsernamePasswordAuthenticationToken(request.username(), request.password()));
    } catch (AuthenticationException e) {
      return null;
    }
    Optional<AppUser> user = userRepository.findByUsername(request.username());
    if (user.isEmpty()) return null;
    AppUser u = user.get();
    String token = tokenProvider.generateToken(u.username(), u.roles());
    return new LoginResponse(token, expirationMinutes * 60L);
  }
}
