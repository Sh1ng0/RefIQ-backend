package com.refiq.platform.modules.ingestion.normalizer;

import static org.assertj.core.api.Assertions.assertThat;


import com.refiq.platform.ingestion.internal.normalizer.CanonicalCSV;
import com.refiq.platform.ingestion.internal.normalizer.CsvNormalizer;
import com.refiq.platform.ingestion.internal.normalizer.NormalizationResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class CsvNormalizerTest {

  private final CsvNormalizer normalizer = new CsvNormalizer();

  // (Happy Paths)

  @ParameterizedTest(name = "Should normalize sex: \"{0}\" -> \"{1}\"")
  @CsvSource({
      "Male,    M",
      "MALE,    M",
      "Hombre,  M",
      "M,       M",
      "Female,  F",
      "Mujer,   F",
      "F,       F",
      "Other,   U", // Caso por defecto
      "Unknown, U"
  })
  @DisplayName("Should correctly normalize sex variants")
  void shouldNormalizeSexVariations(String inputSex, String expectedSex) {
    // Given: ID;Age;DateOfBirth;Sex;Ignored;Value
    String[] row = {"1", "30", "1990-01-01", inputSex, "ignored", "10.5"};

    // When
    var result = (NormalizationResult.Success) normalizer.normalize(row);

    // Then
    assertThat(result.data().sex()).isEqualTo(expectedSex);
  }

  @ParameterizedTest(name = "Should accept and standardize numeric value: \"{0}\" -> \"{1}\"")
  // CAMBIO: Usamos delimiter = '|' para evitar conflictos con la coma decimal europea
  @CsvSource(delimiter = '|', textBlock = """
      10.5  | 10.5
      10,5  | 10.5
      -10.5 | -10.5
      0.5   | 0.5
      100   | 100
  """)
  @DisplayName("Should correctly validate and format numeric values")
  void shouldHandleValidNumericFormats(String inputValue, String expectedValue) {
    // Given
    // trim() es importante aquí porque el textBlock puede dejar espacios si no alineamos perfecto
    String val = inputValue.trim();
    String expected = expectedValue.trim();

    String[] row = {"1", "30", "", "M", "ignored", val};

    // When
    var result = (NormalizationResult.Success) normalizer.normalize(row);

    // Then
    assertThat(result.data().resultValue()).isEqualTo(expected);
  }
  @Test
  @DisplayName("Should correctly normalize a full row (Class integration test)")
  void shouldNormalizeFullRow() {
    // Given: Row completa con mezcla de casos (coma decimal, sexo texto)
    String[] dirtyRow = {"29232-5", "14813", "1982-08-17", "Mujer", "66", "66,5"};

    // When
    var result = (NormalizationResult.Success) normalizer.normalize(dirtyRow);
    CanonicalCSV data = result.data();

    // Then
    assertThat(data.loincCode()).isEqualTo("29232-5");
    assertThat(data.sex()).isEqualTo("F");
    assertThat(data.resultValue()).isEqualTo("66.5"); // Validamos conversión
    assertThat(data.dateOfBirth()).isEqualTo("1982-08-17");
  }


  // --- TESTS DE CASOS DE FALLO (Edge Cases & Errores) ---

  @ParameterizedTest(name = "Should reject invalid value: \"{0}\"")
  @ValueSource(strings = {
      "Texto",        // No numérico obvio
      "12.",          // Punto final sin decimal (Regex estricta)
      ".5",           // Sin cero inicial (Regex estricta)
      "1,200.00",     // Separador de miles no soportado
      "1E5",          // Notación científica (No soportada por Regex simple)
      "12.34.56",     // Doble punto
      "",             // Vacío
      "   "           // Solo espacios
  })
  @DisplayName("Should fail if value does not meet strict numeric format")
  void shouldFailOnInvalidNumericValues(String invalidValue) {
    // Given
    String[] row = {"1", "30", "", "M", "ignored", invalidValue};

    // When
    NormalizationResult result = normalizer.normalize(row);

    // Then
    assertThat(result).isInstanceOf(NormalizationResult.Failure.class);
    assertThat(((NormalizationResult.Failure) result).reason())
        .as("El mensaje de error debería mencionar el valor inválido para debug")
        .contains(invalidValue.trim()); // El normalizador hace trim() antes de fallar
  }

  @Test
  @DisplayName("Should fail if required columns are missing")
  void shouldFailOnMissingColumns() {
    String[] incompleteRow = {"ID", "Age", "DOB"}; // Solo 3 columnas

    NormalizationResult result = normalizer.normalize(incompleteRow);

    assertThat(result).isInstanceOf(NormalizationResult.Failure.class);
    assertThat(((NormalizationResult.Failure) result).reason())
        .contains("Faltan columnas");
  }
}