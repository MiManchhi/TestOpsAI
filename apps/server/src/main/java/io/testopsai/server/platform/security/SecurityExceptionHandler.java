package io.testopsai.server.platform.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.testopsai.server.platform.error.ErrorResponse;
import io.testopsai.server.platform.error.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

@Component
class SecurityExceptionHandler implements AuthenticationEntryPoint, AccessDeniedHandler {
  private final ObjectMapper objectMapper;

  SecurityExceptionHandler(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public void commence(
      HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
      throws IOException {
    writeError(response, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication required");
  }

  @Override
  public void handle(
      HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex)
      throws IOException {
    writeError(response, HttpStatus.FORBIDDEN, "FORBIDDEN", "Access denied");
  }

  void writeError(HttpServletResponse response, HttpStatus status, String code, String message)
      throws IOException {
    String requestId = MDC.get(RequestIdFilter.MDC_KEY);
    if (requestId == null) requestId = "unknown";
    response.setStatus(status.value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    objectMapper.writeValue(response.getWriter(), new ErrorResponse(code, message, requestId));
  }
}
