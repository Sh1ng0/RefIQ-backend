package com.refiq.platform.calculation.api.web;

import com.refiq.platform.calculation.api.web.response.ResultResponse;
import com.refiq.platform.calculation.api.web.response.ResultWebResponse;
import com.refiq.platform.calculation.internal.domain.CalculationState;
import com.refiq.platform.calculation.internal.repository.DbCalculationResultRepository;
import com.refiq.platform.shared.web.ApiError;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes the calculation state machine to external clients.
 * <p>
 * Allows the frontend to poll for the asynchronous calculation status using the tracking ID
 * provided during the initial ingestion phase.
 * </p>
 */
@RestController
@RequestMapping("/api/v1/results")
public class ResultController implements ResultApi {

  private final DbCalculationResultRepository repository;

  public ResultController(DbCalculationResultRepository repository) {
    this.repository = repository;
  }

  /**
   * Retrieves the current processing state or the final JSON result of a calculation.
   *
   * @param fileId The unique correlation identifier assigned during file ingestion.
   * @return A {@link ResponseEntity} containing the typed status and the corresponding HTTP status
   * code.
   */
  @Override
  public ResponseEntity<ResultWebResponse> getResult(@PathVariable UUID fileId) {
    return repository.findById(fileId)
        .map(state -> {

          ResultWebResponse responseBody = switch (state) {
            case CalculationState.Pending p ->
                new ResultWebResponse.Success(new ResultResponse.Pending("Calculation is pending"));

            case CalculationState.Processing p -> new ResultWebResponse.Success(
                new ResultResponse.Processing("Calculation in progress"));

            case CalculationState.Failed f ->
                new ResultWebResponse.Failure(new ApiError("Calculation error", f.errorMessage()));

            case CalculationState.Success s ->
                new ResultWebResponse.Success(new ResultResponse.Success(s.payload()));
          };

          HttpStatus status = switch (state) {
            case CalculationState.Failed f -> HttpStatus.INTERNAL_SERVER_ERROR;
            case CalculationState.Success s -> HttpStatus.OK;
            default -> HttpStatus.ACCEPTED;
          };

          var responseEntityBuilder = ResponseEntity.status(status);

          if (state instanceof CalculationState.Success) {
            responseEntityBuilder.header(HttpHeaders.CONTENT_TYPE,
                MediaType.APPLICATION_JSON_VALUE);
          }

          return responseEntityBuilder.body(responseBody);
        })
        .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(new ResultWebResponse.Failure(
                new ApiError("No results found for the provided ID.")
            ))
        );
  }
}