package com.refiq.platform.calculation.api.web;




import com.refiq.platform.calculation.internal.repository.CalculationResultRepository;
import com.refiq.platform.calculation.internal.repository.entity.CalculationStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * REST Controller responsible for retrieving the final results of asynchronous calculations.
 * <p>
 * This controller provides an endpoint for clients (e.g., the Frontend) to poll the status
 * of a calculation using the unique file identifier (UUID) generated during the initial
 * ingestion phase. It interacts with the {@link CalculationResultRepository} to fetch
 * the current state of the processing pipeline.
 * </p>
 */
@RestController
@RequestMapping("/api/v1/results")
@RequiredArgsConstructor
public class ResultController {

  private final CalculationResultRepository repository;

  /**
   * Retrieves the calculation result or the current processing status for a given file ID.
   * <p>
   * This endpoint relies on HTTP status codes to communicate the state of the asynchronous process
   * to the polling client:
   * <ul>
   * <li><b>200 OK:</b> The calculation is complete, and the final JSON payload is returned.</li>
   * <li><b>202 ACCEPTED:</b> The calculation is still in progress in the Data Lake or the R engine.</li>
   * <li><b>500 INTERNAL SERVER ERROR:</b> The processing failed, returning the underlying error message.</li>
   * <li><b>404 NOT FOUND:</b> The provided UUID does not exist in the tracking database.</li>
   * </ul>
   * </p>
   *
   * @param fileId The unique identifier (UUID) of the ingested file to query.
   * @return A {@link ResponseEntity} containing the exact JSON payload on success, a status message
   * if pending, or an error payload if the calculation failed.
   */
  @GetMapping("/{fileId}")
  public ResponseEntity<?> getResult(@PathVariable UUID fileId) {
    return repository.findById(fileId)
        .map(entity -> {
          if (entity.getStatus() == CalculationStatus.PENDING) {
            // 202 Accepted: El Data Lake / R aún están currando
            return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body("{\"message\": \"Calculation in progress\"}");
          } else if (entity.getStatus() == CalculationStatus.FAILED) {
            // 500 o 400: Algo falló. Devolvemos el mensaje de error.
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("{\"error\": \"" + entity.getErrorMessage() + "\"}");
          } else {
            // 200 OK: ¡Éxito! Devolvemos el JSON exacto que guardamos.
            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(entity.getPayload());
          }
        })
        // 404: Si el UUID no existe en la base de datos
        .orElseGet(() -> ResponseEntity.notFound().build());
  }
}