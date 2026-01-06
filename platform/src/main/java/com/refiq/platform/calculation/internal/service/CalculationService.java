package com.refiq.platform.calculation.internal.service;

import com.refiq.platform.calculation.api.dto.CalculationRequest;
import com.refiq.platform.calculation.api.dto.CalculationResponse;
import com.refiq.platform.calculation.api.dto.CalculationResult;
import com.refiq.platform.calculation.internal.port.AnalysisPort;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CalculationService {

  private static final Logger log = LoggerFactory.getLogger(CalculationService.class);
  private final AnalysisPort analysisPort; // El adaptador de Plumber

  public CalculationResult runAnalysis(CalculationRequest request) {

    CalculationLogEvent.CALCULATION_STARTED.log(log, request.s3Key(), request.percentileLow(), request.percentileHigh());

    try {

      CalculationResponse response = analysisPort.calculate(request);


      CalculationLogEvent.CALCULATION_COMPLETED.log(log, request.s3Key());

      return new CalculationResult.Success(response);

    } catch (Exception e) {
      CalculationLogEvent.PLUMBER_ERROR.log(log, e.getMessage());
      return new CalculationResult.EngineUnavailable(e.getMessage());
    }
  }
}