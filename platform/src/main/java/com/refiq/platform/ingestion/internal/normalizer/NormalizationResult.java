package com.refiq.platform.ingestion.internal.normalizer;

/**
 * Represents the sealed result of a row normalization attempt.
 * <p>
 * This interface facilitates exhaustive pattern matching to handle the outcome of processing
 * a single CSV row (success, failure, or ignored) without exceptions.
 * </p>
 */
public sealed interface NormalizationResult {

  /**
   * The row was successfully normalized and converted to the canonical format.
   */
  record Success(CanonicalCSV data) implements NormalizationResult {

  }


  /**
   * The row could not be normalized due to validation errors (e.g., non-numeric values, missing columns).
   *
   * @param reason      Description of the validation failure.
   * @param originalRow The raw content of the failed row for logging/debugging.
   */
  record Failure(String reason, String[] originalRow) implements NormalizationResult {

  }

  /**
   * The row was explicitly ignored (e.g., empty lines, comments) and should be skipped silently.
   */
  record Ignored() implements NormalizationResult {

  }
}