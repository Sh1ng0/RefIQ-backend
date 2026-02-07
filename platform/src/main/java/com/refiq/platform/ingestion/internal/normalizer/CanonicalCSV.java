package com.refiq.platform.ingestion.internal.normalizer;


/**
 * Contrato de datos normalizados para RefineR.
 * Estandariza la salida a CSV: Separador ',' y Decimal '.'
 */
public record CanonicalCSV(
    String loincCode,
    String ageDays,
    String dateOfBirth,
    String sex,
    String resultValue,
    String unit,
    String rowHash         // Trazabilidad
) {

  // Nombres de columnas que R usará para leer (Data$value, Data$sex...)
  public static final String[] HEADERS = {
      "loinc_code", "age_days", "date_of_birth", "sex", "value", "unit", "row_hash"
  };

  /**
   * Serializa el record a un array de Strings para OpenCSV.
   * Maneja nulos convirtiéndolos a cadenas vacías.
   */
  public String[] toCsvRow() {
    return new String[] {
        loincCode,
        ageDays,
        dateOfBirth != null ? dateOfBirth : "",
        sex,
        resultValue,
        unit != null ? unit : "",
        rowHash
    };
  }
}