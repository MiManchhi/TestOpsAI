package io.testopsai.server.platform.security;

import io.jsonwebtoken.Claims;
import io.testopsai.server.modules.identity.AppUser;
import io.testopsai.server.modules.identity.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
class JwtAuthenticationFilter extends OncePerRequestFilter {
  private final JwtTokenProvider tokenProvider;
  private final UserRepository users;
  private final SecurityExceptionHandler errors;

  JwtAuthenticationFilter(
      JwtTokenProvider tokenProvider, UserRepository users, SecurityExceptionHandler errors) {
    this.tokenProvider = tokenProvider;
    this.users = users;
    this.errors = errors;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getServletPath();
    return path.equals("/actuator/health")
        || path.startsWith("/actuator/health/")
        || path.equals("/api/v1/auth/login");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String header = request.getHeader("Authorization");
    AppUser user =
        header != null && header.startsWith("Bearer ")
            ? authenticatedUser(header.substring(7))
            : null;
    if (user != null) {
      String uri = request.getRequestURI().substring(request.getContextPath().length());
      boolean passwordRoute =
          ("GET".equals(request.getMethod()) && "/api/v3/account".equals(uri))
              || ("POST".equals(request.getMethod()) && "/api/v3/account/password".equals(uri));
      if (user.mustChangePassword() && !passwordRoute) {
        errors.writeError(
            response,
            HttpStatus.FORBIDDEN,
            "PASSWORD_CHANGE_REQUIRED",
            "Change the temporary password before accessing the workspace");
        return;
      }
      var authorities =
          user.roles().stream().map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList();
      var auth = new UsernamePasswordAuthenticationToken(user.username(), null, authorities);
      auth.setDetails(user.credentialRevision());
      SecurityContextHolder.getContext().setAuthentication(auth);
    }
    chain.doFilter(request, response);
  }

  private AppUser authenticatedUser(String token) {
    Claims claims = tokenProvider.parseToken(token);
    if (claims == null || claims.getSubject() == null) return null;
    Object revision = claims.get("credentialRevision");
    // Access tokens minted before the migration match only an unchanged account.
    long version = revision instanceof Number n ? n.longValue() : 1;
    return users
        .findByUsername(claims.getSubject())
        .filter(user -> user.enabled() && user.credentialRevision() == version)
        .orElse(null);
  }
}
