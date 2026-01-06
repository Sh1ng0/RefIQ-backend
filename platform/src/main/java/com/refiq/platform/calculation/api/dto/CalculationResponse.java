package com.refiq.platform.calculation.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

/**
 * Representa la respuesta final del motor de cálculo. Mapea el JSON definido en Jira para el MVP.
 */
public record CalculationResponse(
    @JsonProperty("lab_result") LabResult labResult,
    Map<String, Double> parameters
) {

  // Record interno para separar los parámetros técnicos de los datos médicos
  public record LabResult(
      @JsonProperty("test_code") String testCode,
      String name,
      Double value, // Puede ser null si el CSV solo tiene población
      String unit,
      @JsonProperty("reference_range") String referenceRange, // Formato "70-100"
      String notes
  ) {

  }
}