package com.refiq.platform.shared.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.io.UncheckedIOException;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Acts as the global exception handling component for the REST API.
 * <p>
 * Functions as an AOP interceptor that catches exceptions thrown by the framework and
 * technical infrastructure failures, translating them into structured HTTP responses.
 * </p>
 * <p>
 * Implements the Envelope Pattern, ensuring that all errors maintain structural consistency
 * with domain-specific failures:
 * <pre>
 * {
 *   "error": {
 *     "error": "General description",
 *     "details": {
 *       "field": "Specific error message"
 *     }
 *   }
 * }
 * </pre>
 * </p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<Map<String, ApiError>> handleValidationErrors(MethodArgumentNotValidException ex) {
    var errors = ex.getBindingResult().getFieldErrors().stream()
        .collect(Collectors.toMap(
            FieldError::getField,
            e -> e.getDefaultMessage() != null ? e.getDefaultMessage() : "Validation error",
            (existing, replacement) -> existing
        ));

    ApiError apiError = new ApiError("Validation error in the provided data", errors);
    return ResponseEntity.badRequest().body(Map.of("error", apiError));
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  public ResponseEntity<Map<String, ApiError>> handleMaxSizeException(MaxUploadSizeExceededException e) {
    ApiError apiError = new ApiError("The file exceeds the maximum allowed size.");
    return ResponseEntity.status(HttpStatus.EXPECTATION_FAILED).body(Map.of("error", apiError));
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<Map<String, ApiError>> handleMalformedJson(HttpMessageNotReadableException e) {
    ApiError apiError = new ApiError("The request body (JSON) is missing or malformed.");
    return ResponseEntity.badRequest().body(Map.of("error", apiError));
  }

  @ExceptionHandler(UncheckedIOException.class)
  public ResponseEntity<Map<String, ApiError>> handleUncheckedIOException(UncheckedIOException e) {
    log.error("Critical I/O infrastructure error", e);
    ApiError apiError = new ApiError("Internal server error while processing the file.");
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", apiError));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<Map<String, ApiError>> handleGenericException(Exception e) {
    log.error("Unhandled platform error", e);
    ApiError apiError = new ApiError("Unexpected server error.");
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", apiError));
  }
}