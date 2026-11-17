package io.testopsai.server.platform.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.testopsai.server.platform.error.ErrorResponse;
import io.testopsai.server.platform.error.RequestIdFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RateLimitFilter extends OncePerRequestFilter {
  private final RateLimiter rateLimiter;
  private final ObjectMapper objectMapper;

  public RateLimitFilter(RateLimiter rateLimiter, ObjectMapper objectMapper) {
    this.rateLimiter = rateLimiter;
    this.objectMapper = objectMapper;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    // Only apply to login endpoint
    return !"/api/v1/auth/login".equals(request.getRequestURI())
        || !"POST".equalsIgnoreCase(request.getMethod());
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String clientIp = getClientIp(request);
    String key = "ratelimit:login:" + clientIp;

    if (!rateLimiter.tryAcquire(key)) {
      long retryAfter = rateLimiter.retryAfterSeconds(key);
      writeRateLimitedResponse(response, retryAfter);
      return;
    }

    chain.doFilter(request, response);
  }

  private String getClientIp(HttpServletRequest request) {
    String forwarded = request.getHeader("X-Forwarded-For");
    if (forwarded != null && !forwarded.isBlank()) {
      return forwarded.split(",")[0].trim();
    }
    return request.getRemoteAddr();
  }

  private void writeRateLimitedResponse(HttpServletResponse response, long retryAfter)
      throws IOException {
    String requestId = MDC.get(RequestIdFilter.MDC_KEY);
    if (requestId == null) requestId = "unknown";
    response.setStatus(429);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setHeader("Retry-After", String.valueOf(retryAfter));
    ErrorResponse error =
        new ErrorResponse("RATE_LIMITED", "Too many login attempts, please retry later", requestId);
    objectMapper.writeValue(response.getWriter(), error);
  }
}
