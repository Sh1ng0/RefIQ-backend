package com.refiq.platform.ingestion.internal.domain;



import java.util.Arrays;
import java.util.Optional;


/**
 * Domain enumeration defining the strictly supported clinical analytes for ingestion.
 * <p>
 * This acts as the single source of truth for routing files to the Data Lake.
 * Any incoming request that cannot be mapped to one of these values will be rejected
 * at the controller level to prevent invalid data from reaching S3.
 * </p>
 */
public enum Analyte {
  ALP, CRE, FT4, GGT, TSH;

  public static Optional<Analyte> fromString(String value) {
    return Arrays.stream(Analyte.values())
        .filter(a -> a.name().equalsIgnoreCase(value))
        .findFirst();
  }
}