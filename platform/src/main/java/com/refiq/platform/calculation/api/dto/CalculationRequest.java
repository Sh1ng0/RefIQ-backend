package com.refiq.platform.calculation.api.dto;



import java.util.Objects;

/**
 * Contrato de entrada para el motor de cálculo (R/Plumber).
 * Contiene la referencia al dato en S3 y los parámetros de confianza estadística.
 */
  public record CalculationRequest(
      String s3Key,
      Double percentileLow,
      Double percentileHigh,
      // LOINC
      String testCode // Por temas de trazabilidad para el back, si el front no envía nada será "null", mirar el script de R y el PlumberAdapter (Línea 50)
      // Y el Plumber adapter
  ) {
  public CalculationRequest {
    Objects.requireNonNull(s3Key, "La s3Key es obligatoria para localizar el archivo");

    // Valores por defecto si vienen nulos desde el Front (Defensivo)
    if (percentileLow == null) percentileLow = 0.025;
    if (percentileHigh == null) percentileHigh = 0.975;

    if (percentileLow >= percentileHigh) {
      throw new IllegalArgumentException("El percentil inferior debe ser menor que el superior");
    }
  }
}