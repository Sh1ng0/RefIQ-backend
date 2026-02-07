package com.refiq.platform.ingestion.internal.domain;



import java.io.InputStream;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Representación agnóstica de un archivo dentro del dominio de Ingesta.
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

 // Helper method para simplificar el código del serviccio
  public InputStream openStream() {
    return contentProvider.get();
  }
}