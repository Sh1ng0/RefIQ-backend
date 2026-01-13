package com.refiq.platform.calculation.internal.port;



import com.refiq.platform.calculation.api.dto.CalculationRequest;
import com.refiq.platform.calculation.api.dto.CalculationResponse;

/**
 * Puerto que define la comunicación con el motor estadístico.
 * Implementa el desacoplamiento entre Java y el elemento ajeno (R).
 */
public interface AnalysisPort {

  /**
   * Ejecuta el análisis estadístico refineR.
   * @param request Datos mínimos (S3Key y Percentiles).
   * @return Respuesta con los rangos calculados.
   */
  CalculationResponse calculate(CalculationRequest request);
}