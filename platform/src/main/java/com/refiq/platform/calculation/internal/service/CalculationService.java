package com.refiq.platform.calculation.internal.service;

import com.refiq.platform.calculation.api.dto.CalculationRequest;
import com.refiq.platform.calculation.api.dto.CalculationResponse;
import com.refiq.platform.calculation.api.dto.CalculationResult;
import com.refiq.platform.calculation.internal.adapter.plumber.PlumberAdapter; // Importamos las excepciones
import com.refiq.platform.calculation.internal.port.AnalysisPort;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.SocketTimeoutException;


/**
 * Service orchestrating the calculation workflow.
 * <p>
 * This service acts as a bridge between the API layer and the statistical engine adapter.
 * Its main role is error handling and transformation: it catches technical exceptions from the
 * adapter (timeouts, engine failures) and maps them to the sealed {@link CalculationResult} domain hierarchy.
 * </p>
 */
@Service
@RequiredArgsConstructor
public class CalculationService {

  private static final Logger log = LoggerFactory.getLogger(CalculationService.class);
  private final AnalysisPort analysisPort;

  /**
   * Triggers the analysis process for a given request.
   *
   * @param request The calculation parameters.
   * @return A {@link CalculationResult} representing Success, DataInconsistency, or EngineUnavailable.
   */
  public CalculationResult runAnalysis(CalculationRequest request) {

    CalculationLogEvent.CALCULATION_STARTED.log(log, request.s3Key(), request.percentileLow(), request.percentileHigh());

    try {
      CalculationResponse response = analysisPort.calculate(request);
      CalculationLogEvent.CALCULATION_COMPLETED.log(log, request.s3Key());

      return new CalculationResult.Success(response);

    } catch (PlumberAdapter.DataInconsistencyException e) {
      // Caso: El CSV está bien formado pero no tiene datos válidos estadísticamente
      return new CalculationResult.DataInconsistency(e.getMessage());

    } catch (PlumberAdapter.EngineUnavailableException e) {
      // Caso: R petó (500)
      return new CalculationResult.EngineUnavailable(e.getMessage());

    } catch (Exception e) {
      // Caso: Timeout de Java, error de red general, o bug desconocido
      CalculationLogEvent.PLUMBER_ERROR.log(log, e.getMessage());

      String debugMsg = e.getMessage();
      // Si es un timeout, lo decimos explícitamente
      if (e.getCause() instanceof SocketTimeoutException) {
        debugMsg = "Timeout esperando respuesta del motor de análisis.";
      }

      return new CalculationResult.EngineUnavailable(debugMsg);
    }
  }
}