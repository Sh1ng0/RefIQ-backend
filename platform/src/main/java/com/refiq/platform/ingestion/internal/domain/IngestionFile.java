package com.refiq.platform.ingestion.internal.domain;



import java.io.InputStream;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Domain-agnostic representation of an ingestion file.
 * <p>
 * This record encapsulates file metadata and a mechanism to access its content, decoupling
 * the domain logic from infrastructure details (like HTTP Multipart files).
 * </p>
 * <p>
 * It uses a {@link Supplier} for the input stream to support multiple reads (e.g., uploading
 * the raw file to storage first, and then reading it again for processing) without exhausting
 * the stream.
 * </p>
 *
 * @param filename        The original name of the file.
 * @param contentProvider A supplier that provides a fresh {@link InputStream} to read the file content.
 * Must not be null.
 * @param size            The size of the file in bytes.
 * @param contentType     The MIME type of the file (e.g., "text/csv").
 * @param cleanupCallback An optional hook to release resources (e.g., deleting temporary files on disk)
 * after the ingestion process is complete.
 */
public record IngestionFile(
    String filename,
    Supplier<InputStream> contentProvider, // De input stream a supplier para el tema del raw s2
    long size,
    String contentType,
    Runnable cleanupCallback
) {

  public IngestionFile {
    Objects.requireNonNull(contentProvider, "El proveedor de contenido es obligatorio");

    if (filename == null || filename.isBlank()) {
      throw new IllegalArgumentException("El nombre del archivo no puede estar vacío");
    }

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