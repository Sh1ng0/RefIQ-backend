package com.refiq.platform.modules.ingestion.normalizer;



import static org.assertj.core.api.Assertions.assertThat;


import com.refiq.platform.ingestion.internal.normalizer.CanonicalCSV;
import com.refiq.platform.ingestion.internal.normalizer.CsvNormalizer;
import com.refiq.platform.ingestion.internal.normalizer.NormalizationResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CsvNormalizerTest {

  private final CsvNormalizer normalizer = new CsvNormalizer();

  @Test
  @DisplayName("Debe normalizar correctamente una fila estándar con formato europeo (; y ,)")
  void shouldNormalizeStandardRow() {
    // Given: ID;Age;DateOfBirth;Sex;ValueOriginalResult;ValueResult
    String[] dirtyRow = {"29232", "14813", "1982-08-17", "F", "66", "66,5"};

    // When
    NormalizationResult result = normalizer.normalize(dirtyRow);

    // Then
    assertThat(result).isInstanceOf(NormalizationResult.Success.class);

    CanonicalCSV canonical = ((NormalizationResult.Success) result).data();

    assertThat(canonical.loincCode()).isEqualTo("29232");
    assertThat(canonical.ageDays()).isEqualTo("14813");
    assertThat(canonical.sex()).isEqualTo("F");
    assertThat(canonical.resultValue()).isEqualTo("66.5"); // OJO: Punto decimal
    assertThat(canonical.dateOfBirth()).isEqualTo("1982-08-17");
  }

  @Test
  @DisplayName("Debe normalizar variantes de sexo (Male/Hombre -> M)")
  void shouldNormalizeSex() {
    String[] dirtyRow = {"1", "100", "", "Hombre", "1", "10"};

    var result = (NormalizationResult.Success) normalizer.normalize(dirtyRow);

    assertThat(result.data().sex()).isEqualTo("M");
  }

  @Test
  @DisplayName("Debe fallar si el valor no es numérico")
  void shouldFailOnNonNumericValue() {

    String[] dirtyRow = {"1", "100", "", "M", "1", "Texto"};

    NormalizationResult result = normalizer.normalize(dirtyRow);

    assertThat(result).isInstanceOf(NormalizationResult.Failure.class);
    assertThat(((NormalizationResult.Failure) result).reason()).contains("Valor no numérico");
  }

  @Test
  @DisplayName("Debe fallar si faltan columnas")
  void shouldFailOnMissingColumns() {
    String[] incompleteRow = {"ID", "Age"};

    NormalizationResult result = normalizer.normalize(incompleteRow);

    assertThat(result).isInstanceOf(NormalizationResult.Failure.class);
  }
}