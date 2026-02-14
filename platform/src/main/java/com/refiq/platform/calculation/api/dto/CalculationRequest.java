package com.refiq.platform.calculation.api.dto;



import java.util.Objects;

/**
 * Input contract for the statistical calculation engine (R/Plumber).
 * <p>
 * This record encapsulates the reference to the dataset (stored in S3) and the statistical parameters
 * required for the analysis.
 * </p>
 *
 * @param s3Key          The key (path) of the CSV file in the S3 bucket. Must not be null.
 * @param percentileLow  The lower bound for the confidence interval (default: 0.025).
 * @param percentileHigh The upper bound for the confidence interval (default: 0.975).
 * @param testCode       The LOINC code or identifier of the test being analyzed, used for traceability
 * and potential engine-side adjustments. Can be null if generic.
 */
  public record CalculationRequest(
      String s3Key,
      Double percentileLow,
      Double percentileHigh,
      // LOINC
      String testCode // Por temas de trazabilidad para el back, si el front no envía nada será "null", mirar el script de R y el PlumberAdapter (Línea 50)
      // Y el Plumber adapter
  ) {


  /**
   * Compact constructor for validation and default value assignment.
   * <p>
   * Applies standard 95% confidence interval defaults (0.025 - 0.975) if percentiles are missing
   * and validates that the lower bound is strictly less than the upper bound.
   * </p>
   */
  public CalculationRequest {
    Objects.requireNonNull(s3Key, "La s3Key es obligatoria");

    // Normalización: Si vienen nulos, asignamos defaults.
    // Al ser compacto, esto modifica el valor que finalmente se guarda en el record.
    if (percentileLow == null) percentileLow = 0.025;
    if (percentileHigh == null) percentileHigh = 0.975;

    // Validación
    if (percentileLow >= percentileHigh) {
      throw new IllegalArgumentException("El percentil inferior debe ser menor que el superior");
    }
  }
}