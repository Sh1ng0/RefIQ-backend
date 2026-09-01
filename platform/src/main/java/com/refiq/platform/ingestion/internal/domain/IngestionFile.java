package com.refiq.platform.ingestion.internal.domain;

import java.io.InputStream;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Domain representation of a file pending ingestion into the Data Lake.
 * <p>
 * Decouples the core routing logic from Spring Web components (like MultipartFile).
 * Uses functional interfaces ({@link Supplier}, {@link Runnable}) to provide lazy access
 * to the file stream and explicit resource cleanup once the transfer is complete.
 * </p>
 *
 * @param filename        The original name of the file provided by the client.
 * @param analyte         The validated clinical analyte (determines the S3 destination folder).
 * @param contentProvider A supplier that provides a fresh {@link InputStream} for the file content.
 * @param size            The total size of the file in bytes.
 * @param contentType     The MIME type of the file.
 * @param cleanupCallback A callback executed after processing to release physical resources.
 */
public record IngestionFile(
    String filename,
    Analyte analyte,
    Supplier<InputStream> contentProvider, // Using Supplier instead of InputStream to handle raw S3 streams
    long size,
    String contentType,
    Runnable cleanupCallback
) {

  public IngestionFile {
    Objects.requireNonNull(contentProvider, "Content provider is required");

    if (filename == null || filename.isBlank()) {
      throw new IllegalArgumentException("Filename cannot be empty");
    }

    Objects.requireNonNull(analyte, "Analyte is required and must be valid");

    if (size < 0) {
      throw new IllegalArgumentException("File size cannot be negative");
    }
  }

  /**
   * Opens a new stream to read the file content.
   *
   * @return A new {@link InputStream} provided by the content supplier.
   */
  public InputStream openStream() {
    return contentProvider.get();
  }
}