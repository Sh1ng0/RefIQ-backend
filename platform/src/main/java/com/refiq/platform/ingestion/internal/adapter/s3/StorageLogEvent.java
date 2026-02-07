package com.refiq.platform.ingestion.internal.adapter.s3;


import com.refiq.platform.shared.observability.Loggable;

/**
 * Eventos de logging específicos para la capa de persistencia (S3).
 * Desacopla los detalles de infraestructura del dominio.
 */
enum StorageLogEvent implements Loggable {

  SINGLE_UPLOAD_SUCCESS(LogLevel.DEBUG, "Subida directa a S3 completada. Bucket: {}, Key: {}"),

  MULTIPART_INITIATED(LogLevel.DEBUG, "Iniciada transacción S3 Multipart. UploadId: {}"),

//  PART_UPLOADED(LogLevel.TRACE, "Parte #{} subida a S3. Key: {}, ETag: {}"),

  MULTIPART_COMPLETED(LogLevel.INFO, "Multipart upload completado exitosamente. Key: {}"),

  MULTIPART_ABORTED(LogLevel.WARN, "Multipart upload abortado (Best Effort). Key: {}, ID: {}"),

  ABORT_FAILED(LogLevel.ERROR, "Fallo al intentar abortar la subida: {}");

  private final LogLevel level;
  private final String template;

  StorageLogEvent(LogLevel level, String template) {
    this.level = level;
    this.template = template;
  }

  @Override
  public LogLevel getLevel() {
    return level;
  }

  @Override
  public String getMessageTemplate() {
    return template;
  }
}