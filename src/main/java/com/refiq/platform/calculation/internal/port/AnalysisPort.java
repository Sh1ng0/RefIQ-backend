package com.refiq.platform.calculation.internal.port;

import com.refiq.platform.calculation.api.dto.CalculationRequest;
import com.refiq.platform.calculation.api.dto.CalculationResponse;

/**
 * Defines the output port contract for communication with the statistical engine.
 * <p>
 * Decouples the core Java application logic from the external R execution environment,
 * adhering to Hexagonal Architecture principles.
 * </p>
 */
// TECHNICAL DEBT
// PENDING DECISION TO KILL @RETRYABLE and make this a sealed interface
@FunctionalInterface
public interface AnalysisPort {

  /**
   * Executes the statistical analysis on the specified dataset.
   *
   * @param request The calculation parameters (S3 Key and percentiles).
   * @return The response containing the calculated reference intervals.
   */
  CalculationResponse calculate(CalculationRequest request);
}