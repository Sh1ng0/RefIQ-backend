package com.refiq.platform.ingestion.internal.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("Ingestion - Domain: Analyte")
class AnalyteTest {

  @Test
  @DisplayName("Debe parsear correctamente un analito exacto")
  void shouldParseExactMatch() {
    // GIVEN & WHEN

    Optional<Analyte> result = Analyte.fromString("TSH");

    // THEN
    assertThat(result).isPresent();
    assertThat(result.get().name()).isEqualTo("TSH");
  }

  @ParameterizedTest
  @ValueSource(strings = {"tsh", " TSH ", "  tSh\n"})
  @DisplayName("Debe parsear correctamente ignorando mayúsculas y espacios en blanco")
  void shouldParseIgnoringCaseAndWhitespace(String noisyInput) {
    // GIVEN & WHEN
    Optional<Analyte> result = Analyte.fromString(noisyInput);

    // THEN

    assertThat(result).isPresent();
    assertThat(result.get().name()).isEqualTo("TSH");
  }

  @Test
  @DisplayName("Debe devolver Optional.empty() para un analito no soportado")
  void shouldReturnEmptyForUnsupported() {
    // GIVEN & WHEN
    Optional<Analyte> result = Analyte.fromString("VITAMINA_C_INVENTADA");

    // THEN
    assertThat(result).isEmpty();
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   ", "\t", "\n"})
  @DisplayName("Debe devolver Optional.empty() para strings nulos o en blanco")
  void shouldReturnEmptyForNullOrBlank(String invalidInput) {
    // GIVEN & WHEN
    Optional<Analyte> result = Analyte.fromString(invalidInput);

    // THEN

    assertThat(result).isEmpty();
  }
}