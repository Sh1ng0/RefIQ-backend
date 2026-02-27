package com.refiq.platform.calculation.api.dto;


import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Objects;

/**
 * Input contract for the statistical calculation engine (R/Plumber).
 * <p>
 * This record encapsulates the reference to the dataset (stored in S3) and the statistical
 * parameters required for the analysis.
 * </p>
 *
 * @param s3Key          The key (path) of the CSV file in the S3 bucket. Must not be null.
 * @param percentileLow  The lower bound for the confidence interval (default: 0.025).
 * @param percentileHigh The upper bound for the confidence interval (default: 0.975).
 * @param testCode       The LOINC code or identifier of the test being analyzed, used for
 *                       traceability and potential engine-side adjustments. Can be null if
 *                       generic.
 */
public record CalculationRequest(

    @Schema(description = "The key (path) of the CSV file in the S3 bucket", example = "canonical/550e8400-e29b-41d4-a716-446655440000.csv", requiredMode = Schema.RequiredMode.REQUIRED)
    String s3Key,

    @Schema(description = "Lower bound for confidence interval", example = "0.025", defaultValue = "0.025")
    Double percentileLow,

    @Schema(description = "Upper bound for confidence interval", example = "0.975", defaultValue = "0.975")
    Double percentileHigh,

    @Schema(description = "LOINC code for traceability", example = "26464-8", nullable = true)
    String testCode
    // Por temas de trazabilidad para el back, si el front no envía nada será "null", mirar el script de R y el PlumberAdapter (Línea 50)
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
    Objects.requireNonNull(s3Key, "The s3Key is mandatory");

    // Normalización: Si vienen nulos, asignamos defaults.
    // Al ser compacto, esto modifica el valor que finalmente se guarda en el record.
    if (percentileLow == null) {
      percentileLow = 0.025;
    }
    if (percentileHigh == null) {
      percentileHigh = 0.975;
    }

    // Validación
    if (percentileLow >= percentileHigh) {
      throw new IllegalArgumentException("Lower percentile must be smaller than upper percentile");
    }
  }
}