package com.refiq.platform.calculation.internal.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.refiq.platform.calculation.api.dto.CalculationRequest;
import com.refiq.platform.calculation.api.dto.CalculationResponse;
import com.refiq.platform.calculation.api.dto.CalculationResult;
import com.refiq.platform.calculation.internal.adapter.plumber.PlumberAdapter;
import com.refiq.platform.calculation.internal.domain.CalculationState;
import com.refiq.platform.calculation.internal.logging.CalculationLogEvent;
import com.refiq.platform.calculation.internal.logging.CalculationTrackingLogEvent;
import com.refiq.platform.calculation.internal.port.AnalysisPort;
import com.refiq.platform.calculation.internal.repository.DbCalculationResultRepository;
import java.net.SocketTimeoutException;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Orchestrates the core calculation workflow for clinical reference intervals.
 * <p>
 * Refactored to Data-Oriented Programming (DOP). Utilizes records and pattern matching for
 * immutable state transitions without relying on JPA entities.
 * </p>
 */
@Service
public class CalculationService {

  private static final Logger log = LoggerFactory.getLogger(CalculationService.class);

  private static final Pattern UUID_PATTERN = Pattern.compile(
      "([a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12})", Pattern.CASE_INSENSITIVE);

  private final AnalysisPort analysisPort;
  private final DbCalculationResultRepository repository;
  private final ObjectMapper objectMapper;

  public CalculationService(AnalysisPort analysisPort, DbCalculationResultRepository repository,
      ObjectMapper objectMapper) {
    this.analysisPort = analysisPort;
    this.repository = repository;
    this.objectMapper = objectMapper;
  }

  /**
   * Triggers the analysis process for a given request triggered by a Data Lake webhook.
   */
  public CalculationResult runAnalysis(CalculationRequest request) {

    new CalculationLogEvent.CalculationStarted(
        request.s3Key(),
        request.percentileLow(),
        request.percentileHigh()
    ).log(log);

    var fileIdOpt = extractUuidFromKey(request.s3Key());

    if (fileIdOpt.isEmpty()) {
      new CalculationLogEvent.CalculationInvalidRequest(request.s3Key()).log(log);
      return new CalculationResult.InvalidRequest(
          "No valid UUID found in the S3 path: " + request.s3Key());
    }

    UUID fileId = fileIdOpt.get();

    int claimed = repository.claimPendingCalculation(fileId);

    if (claimed == 0) {
      return alreadyHandled(fileId);
    }

    CalculationResult finalResult;

    try {
      CalculationResponse response = analysisPort.calculate(request);
      new CalculationLogEvent.CalculationCompleted(request.s3Key()).log(log);
      finalResult = new CalculationResult.Success(response);

    } catch (PlumberAdapter.DataInconsistencyException e) {
      finalResult = new CalculationResult.DataInconsistency(e.getMessage());

    } catch (PlumberAdapter.EngineUnavailableException e) {
      finalResult = new CalculationResult.EngineUnavailable(e.getMessage());

    } catch (Exception e) {
      new CalculationLogEvent.PlumberError(e.getMessage()).log(log);

      String debugMsg = (e.getCause() instanceof SocketTimeoutException)
          ? "Timeout waiting for response from the analysis engine."
          : e.getMessage();

      finalResult = new CalculationResult.EngineUnavailable(debugMsg);
    }

    updateTrackingRecord(fileId, finalResult);

    return finalResult;
  }

  /**
   * Updates the persistent tracking record based on the result of the calculation.
   * <p>
   * Utilizes pattern matching to instantiate the exact state (Success or Failed) and persists it.
   * </p>
   */
  private void updateTrackingRecord(UUID fileId, CalculationResult result) {
    repository.findById(fileId).ifPresentOrElse(currentState -> {

      // API DTO
      CalculationState nextState = switch (result) {
        case CalculationResult.Success success -> {
          try {
            String jsonPayload = objectMapper.writeValueAsString(success.response());
            new CalculationLogEvent.CalculationResultSaved(fileId).log(log);
            yield new CalculationState.Success(fileId, jsonPayload);
          } catch (JsonProcessingException e) {
            yield new CalculationState.Failed(fileId,
                "Error serializing JSON response: " + e.getMessage());
          }
        }
        case CalculationResult.DataInconsistency error -> {
          new CalculationLogEvent.DataInconsistencyTracked(fileId, error.details()).log(log);
          yield new CalculationState.Failed(fileId, error.details());
        }
        case CalculationResult.EngineUnavailable error ->
            new CalculationState.Failed(fileId, error.debugInfo());

        case CalculationResult.InvalidRequest invalid ->
            new CalculationState.Failed(fileId, invalid.reason());

        case CalculationResult.AlreadyHandled ignored -> currentState;
      };

      if (nextState instanceof CalculationState.Success
          || nextState instanceof CalculationState.Failed) {
        repository.update(nextState);
      }


    }, () -> new CalculationTrackingLogEvent.TrackingRecordNotFound(fileId).log(log));
  }

  private Optional<UUID> extractUuidFromKey(String s3Key) {
    Matcher matcher = UUID_PATTERN.matcher(s3Key);
    if (matcher.find()) {
      return Optional.of(UUID.fromString(matcher.group(1)));
    }
    return Optional.empty();
  }

  private CalculationResult alreadyHandled(UUID fileId) {

    String status = repository.findById(fileId)
        .map(state -> switch (state) {
          case CalculationState.Pending p -> "PENDING";
          case CalculationState.Processing p -> "PROCESSING";
          case CalculationState.Success s -> "SUCCESS";
          case CalculationState.Failed f -> "FAILED";
        })
        .orElse("NOT_FOUND");

    new CalculationTrackingLogEvent.TrackingAlreadyHandled(fileId, status).log(log);

    return new CalculationResult.AlreadyHandled(status);
  }
}