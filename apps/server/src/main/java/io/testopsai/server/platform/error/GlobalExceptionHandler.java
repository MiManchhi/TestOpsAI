package io.testopsai.server.platform.error;

import io.testopsai.server.modules.knowledge.KnowledgeBaseNameDuplicateException;
import io.testopsai.server.modules.knowledge.KnowledgeBaseNotFoundException;
import io.testopsai.server.modules.testcase.InvalidSortFieldException;
import io.testopsai.server.modules.testcase.TestCaseNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
class GlobalExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(NoResourceFoundException.class)
  ResponseEntity<ErrorResponse> handleNotFound(NoResourceFoundException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(new ErrorResponse("NOT_FOUND", "Resource not found", currentRequestId()));
  }

  @ExceptionHandler(KnowledgeBaseNotFoundException.class)
  ResponseEntity<ErrorResponse> handleKbNotFound(KnowledgeBaseNotFoundException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(new ErrorResponse("KB_NOT_FOUND", ex.getMessage(), currentRequestId()));
  }

  @ExceptionHandler(TestCaseNotFoundException.class)
  ResponseEntity<ErrorResponse> handleTcNotFound(TestCaseNotFoundException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(new ErrorResponse("TC_NOT_FOUND", ex.getMessage(), currentRequestId()));
  }

  @ExceptionHandler(KnowledgeBaseNameDuplicateException.class)
  ResponseEntity<ErrorResponse> handleKbNameDuplicate(KnowledgeBaseNameDuplicateException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(new ErrorResponse("KB_NAME_DUPLICATE", ex.getMessage(), currentRequestId()));
  }

  @ExceptionHandler(IllegalArgumentException.class)
  ResponseEntity<ErrorResponse> handleValidation(IllegalArgumentException ex) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(new ErrorResponse("VALIDATION_ERROR", ex.getMessage(), currentRequestId()));
  }

  @ExceptionHandler(InvalidSortFieldException.class)
  ResponseEntity<ErrorResponse> handleInvalidSort(InvalidSortFieldException ex) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(new ErrorResponse("VALIDATION_ERROR", ex.getMessage(), currentRequestId()));
  }

  @ExceptionHandler(AccessDeniedException.class)
  ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN)
        .body(new ErrorResponse("FORBIDDEN", ex.getMessage(), currentRequestId()));
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
    log.error("Unexpected error", ex);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(new ErrorResponse("INTERNAL_ERROR", "Internal server error", currentRequestId()));
  }

  private String currentRequestId() {
    String id = MDC.get(RequestIdFilter.MDC_KEY);
    return id != null ? id : "unknown";
  }
}
