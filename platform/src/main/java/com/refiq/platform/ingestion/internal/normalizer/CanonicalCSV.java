package com.refiq.platform.ingestion.internal.normalizer;


/**
 * Normalized data contract intended for the RefineR engine.
 * <p>
 * This record represents a single row of clinical data that has been sanitized and formatted
 * (e.g., using standard CSV delimiters and dot decimal separators) for downstream processing.
 * </p>
 *
 * @param loincCode   The LOINC code identifying the laboratory observation.
 * @param ageDays     The patient's age converted to days.
 * @param dateOfBirth The patient's date of birth (ISO-8601), or an empty string if unavailable.
 * @param sex         Standardized biological sex (e.g., 'M', 'F', 'U').
 * @param resultValue The numerical result value, ensuring a dot ('.') is used as the decimal separator.
 * @param unit        The unit of measure for the result.
 * @param recordId    A unique identifier (UUID) assigned to this specific record for end-to-end traceability.
 */
public record CanonicalCSV(
    String loincCode,
    String ageDays,
    String dateOfBirth,
    String sex,
    String resultValue,
    String unit,
    String recordId       // Trazabilidad
) {

  /**
   * CSV headers expected by the R engine for processing.
   */
  public static final String[] HEADERS = {
      "loinc_code", "age_days", "date_of_birth", "sex", "value", "unit", "row_hash"
  };

  /**
   * Serializes the record fields into a string array for CSV writing.
   * <p>
   * Handles potential null values by converting them to empty strings to maintain CSV structure.
   * </p>
   *
   * @return An ordered array of strings representing the CSV row.
   */
  public String[] toCsvRow() {
    return new String[] {
        loincCode,
        ageDays,
        dateOfBirth != null ? dateOfBirth : "",
        sex,
        resultValue,
        unit != null ? unit : "",
        recordId
    };
  }
}