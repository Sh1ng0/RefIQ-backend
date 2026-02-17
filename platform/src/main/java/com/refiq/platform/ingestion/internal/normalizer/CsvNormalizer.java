package com.refiq.platform.ingestion.internal.normalizer;

import java.util.Arrays;
import java.util.UUID;
import java.util.regex.Pattern; // Import nuevo
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Component responsible for transforming raw CSV rows into the canonical domain format.
 * <p>
 * It applies business rules such as:
 * <ul>
 * <li>Column validation (minimum length).</li>
 * <li>Data cleaning (trimming, decimal separator standardization).</li>
 * <li>Sex normalization (mapping various inputs to standard codes).</li>
 * <li>Traceability assignment (generating a unique Record ID).</li>
 * </ul>
 * This implementation is designed for high throughput, using pre-compiled regex for numeric validation
 * to avoid the overhead of exception handling.
 * </p>
 */

@Component
public class CsvNormalizer {

  private static final Logger log = LoggerFactory.getLogger(CsvNormalizer.class);

  // --- INDICES DEL CSV ---
  private static final int IDX_LOINC = 0;
  private static final int IDX_AGE = 1;
  private static final int IDX_DOB = 2;
  private static final int IDX_SEX = 3;
  private static final int IDX_RESULT = 5;

  // Pre-compiled regex for performance (optional sign, digits, optional decimal part)
  private static final Pattern NUMERIC_PATTERN = Pattern.compile("-?\\d+(\\.\\d+)?");

  /**
   * Normalizes a single raw CSV row.
   *
   * @param row The raw string array representing a CSV line.
   * @return A {@link NormalizationResult} indicating Success (with data), Failure (with reason), or Ignored.
   */
  public NormalizationResult normalize(String[] row) {

    if (row == null || row.length < 6) {
      NormalizationLogEvent.ROW_STRUCTURE_INVALID.log(log, (row != null ? row.length : "null"),
          Arrays.toString(row));
      return new NormalizationResult.Failure("Faltan columnas (min 6)", row);
    }

    try {
      String loinc = row[IDX_LOINC].trim();
      String age = row[IDX_AGE].trim();
      String dob = row[IDX_DOB].trim();

      String sex = switch (row[IDX_SEX].trim().toUpperCase()) {
        case "F", "FEMALE", "MUJER" -> "F";
        case "M", "MALE", "HOMBRE" -> "M";
        default -> "U"; // Unknown
      };

      // Normalización de decimales: Coma europea -> Punto
      String rawValue = row[IDX_RESULT].replace(",", ".").trim();

      // VALIDACIÓN SIN EXCEPCIONES
      if (!isNumeric(rawValue)) {
        NormalizationLogEvent.VALUE_NOT_NUMERIC.log(log, row[IDX_RESULT], Arrays.toString(row));
        return new NormalizationResult.Failure("Valor no numérico: " + row[IDX_RESULT], row);
      }

      var canonical = new CanonicalCSV(
          loinc,
          age,
          dob.isEmpty() ? null : dob,
          sex,
          rawValue,
          "N/A",
          UUID.randomUUID().toString()
      );

      return new NormalizationResult.Success(canonical);

    } catch (Exception e) {
      // Este catch queda solo para errores verdaderamente inesperados (NPE, IndexOutBounds, etc)
      NormalizationLogEvent.UNEXPECTED_PARSING_ERROR.log(log, e.getMessage(), Arrays.toString(row));
      return new NormalizationResult.Failure("Error de parsing: " + e.getMessage(), row);
    }
  }

  /**
   * Checks if a string is numeric using Regex.
   * <p>
   * Efficient alternative to {@code Double.parseDouble()} to avoid exception overhead on invalid data.
   * </p>
   */
  private boolean isNumeric(String str) {
    if (str == null || str.isBlank()) {
      return false;
    }
    // Matcher reutiliza el patrón compilado
    return NUMERIC_PATTERN.matcher(str).matches();
  }
}