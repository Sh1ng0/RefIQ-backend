package com.refiq.platform.ingestion.internal.normalizer;


import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
public class CsvNormalizer {

  private static final Logger log = LoggerFactory.getLogger(CsvNormalizer.class);

  // Model CSV: ID;Age;DateOfBirth;Sex;ValueOriginalResult;ValueResult
  private static final int IDX_LOINC = 0;
  private static final int IDX_AGE = 1;
  private static final int IDX_DOB = 2;
  private static final int IDX_SEX = 3;
  // El 4 es ValueOriginal, nos saltamos al 5
  private static final int IDX_RESULT = 5;

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

      // Cambiamos comas europeas por puntos decimales
      String rawValue = row[IDX_RESULT].replace(",", ".").trim();

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
      NormalizationLogEvent.UNEXPECTED_PARSING_ERROR.log(log, e.getMessage(), Arrays.toString(row));
      return new NormalizationResult.Failure("Error de parsing: " + e.getMessage(), row);
    }
  }


  private boolean isNumeric(String str) {
    if (str == null || str.isBlank()) {
      return false;
    }
    try {
      Double.parseDouble(str);
      return true;
    } catch (NumberFormatException e) {
      return false;
    }
  }
}