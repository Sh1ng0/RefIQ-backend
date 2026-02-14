package com.refiq.platform.calculation.internal.port;



import com.refiq.platform.calculation.api.dto.CalculationRequest;
import com.refiq.platform.calculation.api.dto.CalculationResponse;

/**
 * Output port defining the contract for communication with the statistical engine.
 * <p>
 * This interface decouples the core Java application logic from the external R execution environment,
 * adhering to Hexagonal Architecture principles.
 * </p>
 */
public interface AnalysisPort {

  /**
   * Executes the statistical analysis (RefineR) on the specified dataset.
   *
   * @param request The calculation parameters (S3 Key and percentiles).
   * @return The response containing the calculated reference intervals.
   */
  CalculationResponse calculate(CalculationRequest request);
}