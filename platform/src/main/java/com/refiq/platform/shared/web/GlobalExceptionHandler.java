package com.refiq.platform.shared.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * Componente global de manejo de excepciones para la API REST.
 * <p>
 * Actúa como un interceptor (AOP) que captura excepciones lanzadas por el framework (como fallos de
 * validación en DTOs o JSON mal formados) y las transforma en respuestas HTTP estructuradas y
 * limpias para el cliente.
 * <p>
 * Garantiza que todos los errores de validación sigan un formato consistente:
 * <pre>
 * {
 * "error": "Descripción general",
 * "details": {
 * "campo": "Mensaje de error específico"
 * }
 * }
 * </pre>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<Map<String, Object>> handleValidationErrors(
      MethodArgumentNotValidException ex) {

    Map<String, String> errors = ex.getBindingResult().getFieldErrors().stream()
        .collect(Collectors.toMap(
            FieldError::getField,
            error -> error.getDefaultMessage() != null ? error.getDefaultMessage()
                : "Error de validación",
            (existing, replacement) -> existing
        ));

    return ResponseEntity
        .status(HttpStatus.BAD_REQUEST)
        .body(Map.of(
            "error", "Error de validación en los datos enviados",
            "details", errors
        ));
  }

  @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
  public ResponseEntity<Map<String, String>> handleMalformedJson() {
    return ResponseEntity
        .status(HttpStatus.BAD_REQUEST)
        .body(Map.of("error", "El cuerpo de la petición (JSON) es inválido o falta."));
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  public ResponseEntity<Map<String, String>> handleMaxSizeException(
      MaxUploadSizeExceededException e) {
    return ResponseEntity
        .status(HttpStatus.EXPECTATION_FAILED)
        .body(Map.of("error", "El archivo excede el tamaño máximo permitido."));
  }
}