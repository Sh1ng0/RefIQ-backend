package com.refiq.platform.ingestion.internal.normalizer;


public sealed interface NormalizationResult {

  record Success(CanonicalCSV data) implements NormalizationResult {

  }


  record Failure(String reason, String[] originalRow) implements NormalizationResult {

  }


  record Ignored() implements NormalizationResult {

  }
}