package com.refiq.platform.calculation.api.web;

import com.refiq.platform.calculation.api.dto.CalculationRequest;
import com.refiq.platform.calculation.api.dto.CalculationResponse;
import com.refiq.platform.calculation.api.dto.CalculationResult;
import com.refiq.platform.calculation.internal.service.CalculationService;
import com.refiq.platform.shared.web.ApiError;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/calculations")
@RequiredArgsConstructor
@Tag(name = "2. Calculation", description = "Statistical analysis operations via R/Plumber")
public class CalculationController {

  private final CalculationService calculationService;


  /**
   * Endpoint to execute the reference interval calculation.
   * <p>
   * Accepts a request with an S3 key and returns the calculated clinical results. Maps the internal
   * {@link CalculationResult} to appropriate HTTP status codes:
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
  @Operation(
      summary = "Run Reference Interval Analysis",
      description = "Triggers the RefineR algorithm on a stored Canonical CSV. Returns clinical reference intervals or specific statistical errors."
  )
  @ApiResponses(value = {
      @ApiResponse(
          responseCode = "200",
          description = "Successful Calculation",
          content = @Content(mediaType = "application/json", schema = @Schema(implementation = CalculationResponse.class))
      ),
      @ApiResponse(
          responseCode = "400",
          description = "Invalid Request (Bad parameters or missing S3 key)",
          content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))
      ),
      @ApiResponse(
          responseCode = "422",
          description = "Data Inconsistency (Algorithm failed to converge)",
          content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))
      ),
      @ApiResponse(
          responseCode = "503",
          description = "Engine Unavailable (R/Plumber connection failed)",
          content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))
      )
  })
  @PostMapping("/run")
  public ResponseEntity<?> runAnalysis(@RequestBody CalculationRequest request) {
    CalculationResult result = calculationService.runAnalysis(request);

    return switch (result) {
      // 200 OK
      case CalculationResult.Success s -> ResponseEntity.ok(s.response());

      // 400 Bad Request
      case CalculationResult.InvalidRequest ir -> ResponseEntity.status(HttpStatus.BAD_REQUEST)
          .body(new ApiError("Invalid Request", Map.of("reason", ir.reason())));

      // 503 Service Unavailable
      case CalculationResult.EngineUnavailable eu ->
          ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
              .body(new ApiError("Engine Unavailable", Map.of("debug_info", eu.debugInfo())));

      // 422 Unprocessable Entity
      case CalculationResult.DataInconsistency di ->
          ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
              .body(new ApiError("Data Inconsistency", Map.of("details", di.details())));
    };
  }
}
