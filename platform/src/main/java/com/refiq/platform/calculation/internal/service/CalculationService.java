package com.refiq.platform.calculation.internal.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.refiq.platform.calculation.api.dto.CalculationRequest;
import com.refiq.platform.calculation.api.dto.CalculationResponse;
import com.refiq.platform.calculation.api.dto.CalculationResult;
import com.refiq.platform.calculation.internal.adapter.plumber.PlumberAdapter;
import com.refiq.platform.calculation.internal.domain.CalculationState;
import com.refiq.platform.calculation.internal.port.AnalysisPort;
import com.refiq.platform.calculation.internal.repository.DbCalculationResultRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.SocketTimeoutException;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service orchestrating the core calculation workflow for clinical reference intervals.
 * <p>
 * Refactorizado a DOP (Data-Oriented Programming). Utiliza Records y Pattern Matching
 * para transiciones de estado inmutables sin depender de entidades JPA.
 * </p>
 */
@Service
@RequiredArgsConstructor
public class CalculationService {

  private static final Logger log = LoggerFactory.getLogger(CalculationService.class);

  private static final Pattern UUID_PATTERN = Pattern.compile("([a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12})", Pattern.CASE_INSENSITIVE);

  private final AnalysisPort analysisPort;
  private final DbCalculationResultRepository repository;
  private final ObjectMapper objectMapper;

  /**
   * Triggers the analysis process for a given request triggered by a Data Lake webhook.
   */
  public CalculationResult runAnalysis(CalculationRequest request) {

    CalculationLogEvent.CALCULATION_STARTED.log(
        log,
        request.s3Key(),
        request.percentileLow(),
        request.percentileHigh()
    );

    UUID fileId = extractUuidFromKey(request.s3Key());

    // El cerrojo de concurrencia ahora vive en jOOQ
    int claimed = repository.claimPendingCalculation(fileId);

    if (claimed == 0) {
      return alreadyHandled(fileId);
    }

    CalculationResult finalResult;

    try {
      CalculationResponse response = analysisPort.calculate(request);
      CalculationLogEvent.CALCULATION_COMPLETED.log(log, request.s3Key());
      finalResult = new CalculationResult.Success(response);

    } catch (PlumberAdapter.DataInconsistencyException e) {
      finalResult = new CalculationResult.DataInconsistency(e.getMessage());

    } catch (PlumberAdapter.EngineUnavailableException e) {
      finalResult = new CalculationResult.EngineUnavailable(e.getMessage());

    } catch (Exception e) {
      CalculationLogEvent.PLUMBER_ERROR.log(log, e.getMessage());

      String debugMsg = (e.getCause() instanceof SocketTimeoutException)
          ? "Timeout esperando respuesta del motor de análisis."
          : e.getMessage();

      finalResult = new CalculationResult.EngineUnavailable(debugMsg);
    }

    updateTrackingRecord(fileId, finalResult);

    return finalResult;
  }

  /**
   * Updates the persistent tracking record based on the result of the calculation.
   * Utiliza Pattern Matching para instanciar el estado exacto (Success o Failed) y lo persiste.
   */
  private void updateTrackingRecord(UUID fileId, CalculationResult result) {
    repository.findById(fileId).ifPresentOrElse(currentState -> {

      // API DTO
      CalculationState nextState = switch (result) {
        case CalculationResult.Success success -> {
          try {
            String jsonPayload = objectMapper.writeValueAsString(success.response());
            CalculationLogEvent.CALCULATION_RESULT_SAVED.log(log, fileId);
            yield new CalculationState.Success(fileId, jsonPayload);
          } catch (JsonProcessingException e) {
            yield new CalculationState.Failed(fileId, "Error serializando JSON de respuesta: " + e.getMessage());
          }
        }
        case CalculationResult.DataInconsistency error -> {
          // Reflejando tu comentario: Aquí podríamos inyectar un log más granular en el futuro
          CalculationLogEvent.DATA_INCONSISTENCY_TRACKED.log(log, fileId, error.details());
          yield new CalculationState.Failed(fileId, error.details());
        }
        case CalculationResult.EngineUnavailable error ->
            new CalculationState.Failed(fileId, error.debugInfo());

        case CalculationResult.InvalidRequest invalid ->
            new CalculationState.Failed(fileId, invalid.reason());

        case CalculationResult.AlreadyHandled ignored ->
            currentState;
      };

      if (nextState instanceof CalculationState.Success || nextState instanceof CalculationState.Failed) {
        repository.update(nextState);
      }

    }, () -> CalculationTrackingLogEvent.TRACKING_RECORD_NOT_FOUND.log(log, fileId));
  }

  private UUID extractUuidFromKey(String s3Key) {
    Matcher matcher = UUID_PATTERN.matcher(s3Key);
    if (matcher.find()) {
      return UUID.fromString(matcher.group(1));
    }
    throw new IllegalArgumentException("No se encontró un UUID válido en la ruta de S3: " + s3Key);
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

    CalculationTrackingLogEvent.TRACKING_ALREADY_HANDLED.log(log, fileId, status);

    return new CalculationResult.AlreadyHandled(status);
  }
}