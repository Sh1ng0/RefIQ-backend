package com.refiq.platform.ingestion.internal.domain;


import java.io.InputStream;
import java.util.Objects;

/**
 * Representación agnóstica de un archivo dentro del dominio de Ingesta.
 * <p>
 * Desacopla la lógica de negocio de la infraestructura web (MultipartFile). Al ser un Record, es
 * inmutable y transparente.
 *
 * @param filename    Nombre original del archivo (para trazabilidad).
 * @param content     Stream de datos (para no cargar todo en memoria RAM).
 * @param size        Tamaño en bytes (para validaciones o métricas).
 * @param contentType Tipo MIME (para validación de formato CSV).
 */
public record IngestionFile(
    String filename,
    InputStream content,
    long size,
    String contentType,
    Runnable cleanupCallback  // This closes the temporal file
) {


  public IngestionFile {
    Objects.requireNonNull(content, "El contenido (InputStream) es obligatorio");

    if (filename == null || filename.isBlank()) {
      throw new IllegalArgumentException("El nombre del archivo no puede estar vacío");
    }

    if (size < 0) {
      throw new IllegalArgumentException("El tamaño del archivo no puede ser negativo");
    }
  }
}