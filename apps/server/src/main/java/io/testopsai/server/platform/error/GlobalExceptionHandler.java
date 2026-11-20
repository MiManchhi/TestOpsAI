package io.testopsai.server.platform.error;

import io.testopsai.server.modules.catalog.CaseNotFoundException;
import io.testopsai.server.modules.catalog.RobotCaseReadOnlyException;
import io.testopsai.server.modules.catalog.SelectionTooLargeException;
import io.testopsai.server.modules.catalog.SuiteNotFoundException;
import io.testopsai.server.modules.catalog.VersionConflictException;
import io.testopsai.server.modules.knowledge.KnowledgeBaseNameDuplicateException;
import io.testopsai.server.modules.knowledge.KnowledgeBaseNotFoundException;
import io.testopsai.server.modules.project.LastOwnerException;
import io.testopsai.server.modules.project.NotProjectMemberException;
import io.testopsai.server.modules.project.ProjectKeyDuplicateException;
import io.testopsai.server.modules.project.ProjectNotFoundException;
import io.testopsai.server.modules.testcase.InvalidSortFieldException;
import io.testopsai.server.modules.testcase.TestCaseNotFoundException;
import io.testopsai.server.platform.idempotency.IdempotencyConflictException;
import io.testopsai.server.platform.web.WorkspaceFailure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
class GlobalExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(WorkspaceFailure.class)
  ResponseEntity<ErrorResponse> handleWorkspaceFailure(WorkspaceFailure ex) {
    return ResponseEntity.status(ex.status())
        .body(new ErrorResponse(ex.code(), ex.getMessage(), currentRequestId()));
  }

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

  @ExceptionHandler(ProjectNotFoundException.class)
  ResponseEntity<ErrorResponse> handleProjectNotFound(ProjectNotFoundException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(new ErrorResponse("PROJECT_NOT_FOUND", ex.getMessage(), currentRequestId()));
  }

  @ExceptionHandler(KnowledgeBaseNameDuplicateException.class)
  ResponseEntity<ErrorResponse> handleKbNameDuplicate(KnowledgeBaseNameDuplicateException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(new ErrorResponse("KB_NAME_DUPLICATE", ex.getMessage(), currentRequestId()));
  }

  @ExceptionHandler(ProjectKeyDuplicateException.class)
  ResponseEntity<ErrorResponse> handleProjectKeyDuplicate(ProjectKeyDuplicateException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(new ErrorResponse("PROJECT_KEY_DUPLICATE", ex.getMessage(), currentRequestId()));
  }

  @ExceptionHandler(LastOwnerException.class)
  ResponseEntity<ErrorResponse> handleLastOwner(LastOwnerException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(new ErrorResponse("LAST_OWNER", ex.getMessage(), currentRequestId()));
  }

  @ExceptionHandler(IdempotencyConflictException.class)
  ResponseEntity<ErrorResponse> handleIdempotencyConflict(IdempotencyConflictException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(new ErrorResponse("IDEMPOTENCY_CONFLICT", ex.getMessage(), currentRequestId()));
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

  @ExceptionHandler(NotProjectMemberException.class)
  ResponseEntity<ErrorResponse> handleNotMember(NotProjectMemberException ex) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN)
        .body(new ErrorResponse("FORBIDDEN", ex.getMessage(), currentRequestId()));
  }

  @ExceptionHandler(CaseNotFoundException.class)
  ResponseEntity<ErrorResponse> handleCaseNotFound(CaseNotFoundException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(new ErrorResponse("CASE_NOT_FOUND", ex.getMessage(), currentRequestId()));
  }

  @ExceptionHandler(SuiteNotFoundException.class)
  ResponseEntity<ErrorResponse> handleSuiteNotFound(SuiteNotFoundException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(new ErrorResponse("SUITE_NOT_FOUND", ex.getMessage(), currentRequestId()));
  }

  @ExceptionHandler(VersionConflictException.class)
  ResponseEntity<ErrorResponse> handleVersionConflict(VersionConflictException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(new ErrorResponse("VERSION_CONFLICT", ex.getMessage(), currentRequestId()));
  }

  @ExceptionHandler(RobotCaseReadOnlyException.class)
  ResponseEntity<ErrorResponse> handleRobotCaseReadOnly(RobotCaseReadOnlyException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(new ErrorResponse("ROBOT_CASE_READ_ONLY", ex.getMessage(), currentRequestId()));
  }

  @ExceptionHandler(SelectionTooLargeException.class)
  ResponseEntity<ErrorResponse> handleSelectionTooLarge(SelectionTooLargeException ex) {
    return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
        .body(new ErrorResponse("SELECTION_TOO_LARGE", ex.getMessage(), currentRequestId()));
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(
            new ErrorResponse(
                "VALIDATION_ERROR", "Malformed or unknown request body field", currentRequestId()));
  }

  @ExceptionHandler({
    MethodArgumentTypeMismatchException.class,
    MissingServletRequestParameterException.class,
    MissingRequestHeaderException.class
  })
  ResponseEntity<ErrorResponse> handleBadParameter(Exception ex) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(new ErrorResponse("VALIDATION_ERROR", ex.getMessage(), currentRequestId()));
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
