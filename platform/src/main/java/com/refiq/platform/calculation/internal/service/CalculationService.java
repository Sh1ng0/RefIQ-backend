package com.refiq.platform.calculation.internal.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.refiq.platform.calculation.api.dto.CalculationRequest;
import com.refiq.platform.calculation.api.dto.CalculationResponse;
import com.refiq.platform.calculation.api.dto.CalculationResult;
import com.refiq.platform.calculation.internal.adapter.plumber.PlumberAdapter;

import com.refiq.platform.calculation.internal.port.AnalysisPort;
import com.refiq.platform.calculation.internal.repository.CalculationResultRepository;
import com.refiq.platform.calculation.internal.repository.entity.CalculationStatus;
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
 * This service acts as a critical bridge between the API/Webhook layer, the statistical engine (R/Plumber),
 * and the persistence layer. Its main responsibilities include:
 * <ul>
 * <li>Extracting the correlation ID (UUID) from the Data Lake's S3 object key.</li>
 * <li>Delegating the statistical computation to the {@link AnalysisPort}.</li>
 * <li>Handling and mapping technical/business exceptions into the sealed {@link CalculationResult} hierarchy.</li>
 * <li>Persisting the final outcome (Success or Failure) into the tracking database to support Frontend polling.</li>
 * </ul>
 * </p>
 */
@Service
@RequiredArgsConstructor
public class CalculationService {

  private static final Logger log = LoggerFactory.getLogger(CalculationService.class);

  /**
   * Regex pattern to extract a standard 36-character UUID from a given string.
   */
  private static final Pattern UUID_PATTERN = Pattern.compile("([a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12})", Pattern.CASE_INSENSITIVE);

  private final AnalysisPort analysisPort;
  private final CalculationResultRepository repository;
  private final ObjectMapper objectMapper;

  /**
   * Triggers the analysis process for a given request triggered by a Data Lake webhook.
   * <p>
   * This method executes the full business transaction: extracts the file ID, communicates with the
   * R engine, maps any resulting errors, and updates the database record. It guarantees that the
   * database is updated with either the resulting JSON payload or a corresponding error message.
   * </p>
   *
   * @param request The calculation parameters, including the S3 key of the Gold layer file.
   * @return A {@link CalculationResult} representing a {@code Success}, {@code DataInconsistency},
   * or {@code EngineUnavailable}.
   */
  public CalculationResult runAnalysis(CalculationRequest request) {

    CalculationLogEvent.CALCULATION_STARTED.log(
        log,
        request.s3Key(),
        request.percentileLow(),
        request.percentileHigh()
    );

    // 1. Extraemos el UUID de la ruta del archivo (S3 Key)
    UUID fileId = extractUuidFromKey(request.s3Key());

    int claimed = repository.claimPendingCalculation(fileId);

    if (claimed == 0) {
      return alreadyHandled(fileId);
    }


    CalculationResult finalResult;

    // 2. Ejecutamos el análisis con tu manejo de errores intacto
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
   * <p>
   * If the calculation is successful, the payload is serialized into JSON and saved.
   * If an error occurred, the status is updated to FAILED and the error message is stored.
   * </p>
   *
   * @param fileId The unique identifier of the file/calculation being tracked.
   * @param result The outcome of the calculation process to be persisted.
   */
  private void updateTrackingRecord(UUID fileId, CalculationResult result) {
    repository.findById(fileId).ifPresentOrElse(entity -> {

      if (result instanceof CalculationResult.Success success) {
        try {
          String jsonPayload = objectMapper.writeValueAsString(success.response());
          entity.setStatus(CalculationStatus.SUCCESS);
          entity.setPayload(jsonPayload);
          CalculationLogEvent.CALCULATION_RESULT_SAVED.log(log, fileId);
        } catch (JsonProcessingException e) {
          entity.setStatus(CalculationStatus.FAILED);
          entity.setErrorMessage("Error serializando JSON de respuesta: " + e.getMessage());
        }
      }
      else if (result instanceof CalculationResult.DataInconsistency error) {
        entity.setStatus(CalculationStatus.FAILED);
        entity.setErrorMessage(error.details());
      }
      else if (result instanceof CalculationResult.EngineUnavailable error) {
        entity.setStatus(CalculationStatus.FAILED);
        entity.setErrorMessage(error.debugInfo());
      }

      repository.save(entity);

    }, () -> CalculationTrackingLogEvent.TRACKING_RECORD_NOT_FOUND.log(log, fileId));
  }

  /**
   * Extracts the Correlation ID (UUID) embedded within the S3 object key.
   *
   * @param s3Key The full path of the file in the Data Lake (e.g., "3.Gold/TSH/TSH_123e4567...parquet").
   * @return The extracted {@link UUID}.
   * @throws IllegalArgumentException if no valid UUID is found in the provided key.
   */
  private UUID extractUuidFromKey(String s3Key) {
    Matcher matcher = UUID_PATTERN.matcher(s3Key);
    if (matcher.find()) {
      return UUID.fromString(matcher.group(1));
    }
    throw new IllegalArgumentException("No se encontró un UUID válido en la ruta de S3: " + s3Key);
  }


  private CalculationResult alreadyHandled(UUID fileId) {
    String status = repository.findById(fileId)
        .map(entity -> entity.getStatus().name())
        .orElse("NOT_FOUND");

    CalculationTrackingLogEvent.TRACKING_ALREADY_HANDLED.log(log, fileId, status);

    return new CalculationResult.AlreadyHandled(status);
  }
}