package com.refiq.platform.ingestion.internal.service;



import com.refiq.platform.shared.observability.Loggable;

/**
 * Catálogo de eventos de log para el módulo de Ingesta.
 */
enum IngestionLogEvent implements Loggable {

  UPLOAD_INITIATED(LogLevel.DEBUG, "Iniciando carga de archivo. Nombre original: {}, Tamaño: {} bytes"),

  FILE_VALIDATION_FAILED(LogLevel.WARN, "Archivo rechazado por validación. Motivo: {}"),

  UPLOAD_SUCCESS(LogLevel.INFO, "Archivo subido correctamente a S3. ID: {}, Key: {}"),

  STORAGE_ERROR(LogLevel.ERROR, "Fallo crítico al subir archivo al storage. ID: {}, Error: {}"),

  ASYNC_PROCESS_STARTED(LogLevel.INFO, "Proceso de ingesta asíncrona aceptado. ID: {}, Key: {}"),

  VALIDATION_WARNING(LogLevel.WARN,"Línea ignorada por formato inválido. Esperadas: {}, Encontradas: {}. Contenido: '{}'"),
  INGESTION_COMPLETED_WITH_WARNINGS(LogLevel.INFO,"Ingesta finalizada con advertencias. Líneas procesadas: {}, Líneas ignoradas: {}"),

  MULTIPART_INITIATED(LogLevel.DEBUG, "Iniciada transacción S3 Multipart. UploadId: {}"),

  PART_UPLOADED(LogLevel.DEBUG, "Parte #{} subida correctamente. ETag: {}"),

  MULTIPART_COMPLETED(LogLevel.INFO, "Ingesta finalizada exitosamente. Total partes: {}"),

  MULTIPART_ABORTED(LogLevel.ERROR, "Proceso abortado por error crítico. ID: {}, Motivo: {}");

  private final LogLevel level;
  private final String template;

  IngestionLogEvent(LogLevel level, String template) {
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