package com.refiq.platform.calculation.api.web;

import com.refiq.platform.calculation.api.dto.CalculationRequest;
import com.refiq.platform.calculation.api.dto.CalculationResult;
import com.refiq.platform.calculation.internal.service.CalculationService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/calculations")
@RequiredArgsConstructor
public class CalculationController {

  private final CalculationService calculationService;


  /**
   * Endpoint to execute the reference interval calculation.
   * <p>
   * Accepts a request with an S3 key and returns the calculated clinical results.
   * Maps the internal {@link CalculationResult} to appropriate HTTP status codes:
   * <ul>
   * <li>200 OK: Successful calculation.</li>
   * <li>400 BAD REQUEST: Invalid input parameters.</li>
   * <li>422 UNPROCESSABLE ENTITY: Data inconsistency (statistically invalid data).</li>
   * <li>503 SERVICE UNAVAILABLE: R engine failure or timeout.</li>
   * </ul>
   * </p>
   *
   * @param request The calculation request body.
   * @return The HTTP response entity containing the result or error details.
   */
  @PostMapping("/run")
  public ResponseEntity<?> runAnalysis(@RequestBody CalculationRequest request) {
    CalculationResult result = calculationService.runAnalysis(request);

    return switch (result) {
      case CalculationResult.Success s -> ResponseEntity.ok(s.response());
      // 400
      case CalculationResult.InvalidRequest ir ->
          ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
              "error", "Invalid Request",
              "reason", ir.reason()
          ));
      // 503
      case CalculationResult.EngineUnavailable eu ->
          ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
              "error", "Engine Unavailable",
              "debug_info", eu.debugInfo()
          ));
      // 422
      case CalculationResult.DataInconsistency di ->
          ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of(
              "error", "Data Inconsistency",
              "details", di.details()
          ));
    };
  }
}