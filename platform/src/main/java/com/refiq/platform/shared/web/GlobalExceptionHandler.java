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
 * Componente global de manejo de excepciones para la API REST.
 * <p>
 * Actúa como un interceptor (AOP) que captura excepciones lanzadas por el framework y
 * fallos técnicos de infraestructura, transformándolos en respuestas HTTP estructuradas.
 * <p>
 * Implementa el Patrón Envoltorio (Envelope), garantizando que todos los errores
 * mantengan consistencia estructural con los fallos de dominio:
 * <pre>
 * {
 *   "error": {
 *     "error": "Descripción general",
 *     "details": {
 *       "campo": "Mensaje de error específico"
 *     }
 *   }
 * }
 * </pre>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<Map<String, ApiError>> handleValidationErrors(MethodArgumentNotValidException ex) {
    var errors = ex.getBindingResult().getFieldErrors().stream()
        .collect(Collectors.toMap(
            FieldError::getField,
            e -> e.getDefaultMessage() != null ? e.getDefaultMessage() : "Error de validación",
            (existing, replacement) -> existing
        ));

    ApiError apiError = new ApiError("Error de validación en los datos enviados", errors);
    return ResponseEntity.badRequest().body(Map.of("error", apiError));
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  public ResponseEntity<Map<String, ApiError>> handleMaxSizeException(MaxUploadSizeExceededException e) {
    ApiError apiError = new ApiError("El archivo excede el tamaño máximo permitido");
    return ResponseEntity.status(HttpStatus.EXPECTATION_FAILED).body(Map.of("error", apiError));
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<Map<String, ApiError>> handleMalformedJson(HttpMessageNotReadableException e) {
    ApiError apiError = new ApiError("El cuerpo de la petición (JSON) es inválido o falta.");
    return ResponseEntity.badRequest().body(Map.of("error", apiError));
  }

  @ExceptionHandler(UncheckedIOException.class)
  public ResponseEntity<Map<String, ApiError>> handleUncheckedIOException(UncheckedIOException e) {
    log.error("Error crítico de I/O en la infraestructura", e);
    ApiError apiError = new ApiError("Error interno del servidor al procesar el archivo.");
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", apiError));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<Map<String, ApiError>> handleGenericException(Exception e) {
    log.error("Error no controlado en la plataforma", e);
    ApiError apiError = new ApiError("Error inesperado en el servidor.");
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", apiError));
  }
}