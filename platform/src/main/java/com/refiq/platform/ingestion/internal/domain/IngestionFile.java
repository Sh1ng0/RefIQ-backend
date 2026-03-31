package com.refiq.platform.ingestion.internal.domain;



import java.io.InputStream;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Domain representation of a file pending ingestion into the Data Lake.
 * <p>
 * This record decouples the core routing logic from Spring Web components (like MultipartFile).
 * It uses functional interfaces ({@link Supplier}, {@link Runnable}) to provide lazy access
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
    Supplier<InputStream> contentProvider, // De input stream a supplier para el tema del raw s3
    long size,
    String contentType,
    Runnable cleanupCallback
) {

  public IngestionFile {
    Objects.requireNonNull(contentProvider, "El proveedor de contenido es obligatorio");

    if (filename == null || filename.isBlank()) {
      throw new IllegalArgumentException("El nombre del archivo no puede estar vacío");
    }

    Objects.requireNonNull(analyte, "El analito es obligatorio y debe ser válido");

    if (size < 0) {
      throw new IllegalArgumentException("El tamaño del archivo no puede ser negativo");
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