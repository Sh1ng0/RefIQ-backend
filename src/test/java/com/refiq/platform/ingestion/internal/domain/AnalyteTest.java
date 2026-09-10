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
  @DisplayName("Should correctly parse an exact match")
  void shouldParseExactMatch() {
    // GIVEN & WHEN
    Optional<Analyte> result = Analyte.fromString("TSH");

    // THEN
    assertThat(result).isPresent();
    assertThat(result.get().name()).isEqualTo("TSH");
  }

  @ParameterizedTest
  @ValueSource(strings = {"tsh", " TSH ", "  tSh\n"})
  @DisplayName("Should correctly parse while ignoring case and whitespace")
  void shouldParseIgnoringCaseAndWhitespace(String noisyInput) {
    // GIVEN & WHEN
    Optional<Analyte> result = Analyte.fromString(noisyInput);

    // THEN
    assertThat(result).isPresent();
    assertThat(result.get().name()).isEqualTo("TSH");
  }

  @Test
  @DisplayName("Should return Optional.empty() for an unsupported analyte")
  void shouldReturnEmptyForUnsupported() {
    // GIVEN & WHEN
    Optional<Analyte> result = Analyte.fromString("FAKE_VITAMIN_C");

    // THEN
    assertThat(result).isEmpty();
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   ", "\t", "\n"})
  @DisplayName("Should return Optional.empty() for null or blank strings")
  void shouldReturnEmptyForNullOrBlank(String invalidInput) {
    // GIVEN & WHEN
    Optional<Analyte> result = Analyte.fromString(invalidInput);

    // THEN
    assertThat(result).isEmpty();
  }
}