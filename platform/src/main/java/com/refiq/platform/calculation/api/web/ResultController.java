package com.refiq.platform.calculation.api.web;


import com.refiq.platform.calculation.api.web.response.ResultResponse;
import com.refiq.platform.calculation.api.web.response.ResultWebResponse;
import com.refiq.platform.calculation.internal.repository.CalculationResultRepository;
import com.refiq.platform.shared.web.ApiError;
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
 * This controller provides an endpoint for clients (e.g., the Frontend) to poll the status of a
 * calculation using the unique file identifier (UUID) generated during the initial ingestion phase.
 * It interacts with the {@link CalculationResultRepository} to fetch the current state of the
 * processing pipeline.
 * </p>
 */
@RestController
@RequestMapping("/api/v1/results")
@RequiredArgsConstructor
public class ResultController implements ResultApi{

  private final CalculationResultRepository repository;

  /**
   * Retrieves the calculation result or the current processing status for a given file ID.
   * <p>
   * This endpoint relies on HTTP status codes and a strongly typed {@link ResultWebResponse}
   * envelope to communicate the state of the asynchronous process to the polling client:
   * <ul>
   * <li><b>200 OK:</b> The calculation is complete. Returns a {@link ResultWebResponse.Success}
   * wrapping a {@link ResultResponse.Success} with the final JSON payload in the data field.</li>
   * <li><b>202 ACCEPTED:</b> The calculation is queued or still in progress. Returns a
   * {@link ResultWebResponse.Success} wrapping a {@link ResultResponse.Pending} or
   * {@link ResultResponse.Processing} state.</li>
   * <li><b>500 INTERNAL SERVER ERROR:</b> The processing failed. Returns a
   * {@link ResultWebResponse.Failure} encapsulating an {@link ApiError} detailing the underlying issue.</li>
   * <li><b>404 NOT FOUND:</b> The provided UUID does not exist in the tracking database.
   * Returns a {@link ResultWebResponse.Failure} wrapping an {@link ApiError}.</li>
   * </ul>
   * </p>
   *
   * @param fileId The unique identifier (UUID) of the ingested file to query.
   * @return A {@link ResponseEntity} containing a {@link ResultWebResponse} that safely encapsulates
   * the current state (data payload or error details) of the calculation.
   */
  // Mirar si se puede quitar los "news"
  @Override
  public ResponseEntity<ResultWebResponse> getResult(@PathVariable UUID fileId) {
    return repository.findById(fileId)
        .map(entity -> switch (entity.getStatus()) {

          case PENDING -> ResponseEntity.status(HttpStatus.ACCEPTED)
              .body((ResultWebResponse) new ResultWebResponse.Success(
                  new ResultResponse.Pending("Calculation is pending")
              ));

          case PROCESSING -> ResponseEntity.status(HttpStatus.ACCEPTED)
              .body((ResultWebResponse) new ResultWebResponse.Success(
                  new ResultResponse.Processing("Calculation in progress")
              ));

          case FAILED -> ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
              .body((ResultWebResponse) new ResultWebResponse.Failure(
                  new ApiError("Error en el cálculo", entity.getErrorMessage())
              ));

          case SUCCESS -> ResponseEntity.ok()
              .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
              .body((ResultWebResponse) new ResultWebResponse.Success(
                  new ResultResponse.Success(entity.getPayload())
              ));
        })
        .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(new ResultWebResponse.Failure(
                new ApiError("No se encontraron resultados para el ID proporcionado.")
            ))
        );
  }
}
