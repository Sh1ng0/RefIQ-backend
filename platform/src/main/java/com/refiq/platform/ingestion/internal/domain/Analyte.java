package com.refiq.platform.ingestion.internal.domain;

import java.util.Arrays;
import java.util.Optional;

/**
 * Defines the strictly supported clinical analytes for ingestion.
 * <p>
 * Acts as the single source of truth for routing files to the Data Lake.
 * Any incoming request that cannot be mapped to one of these values will be rejected
 * at the controller level to prevent invalid data from reaching S3.
 * </p>
 */
public enum Analyte {
  ALP, CRE, FT4, GGT, TSH;

  public static Optional<Analyte> fromString(String value) {
    if (value == null || value.isBlank()) {
      return Optional.empty();
    }

    String cleanValue = value.trim();
    return Arrays.stream(Analyte.values())
        .filter(a -> a.name().equalsIgnoreCase(cleanValue))
        .findFirst();
  }
}