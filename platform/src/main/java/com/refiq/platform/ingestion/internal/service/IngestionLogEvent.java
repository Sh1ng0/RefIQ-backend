package com.refiq.platform.ingestion.internal.service;



import com.refiq.platform.shared.observability.Loggable;

/**
 * Catálogo de eventos de log para el módulo de Ingesta.
 */
enum IngestionLogEvent implements Loggable {

  UPLOAD_INITIATED(LogLevel.DEBUG, "Iniciando carga de archivo. Nombre original: {}, Tamaño: {} bytes"),

  FILE_VALIDATION_FAILED(LogLevel.WARN, "Archivo rechazado por validación. Motivo: {}"),

  UPLOAD_SUCCESS(LogLevel.INFO, "Archivo subido correctamente a S3. ID: {}, Key: {}"),

  STORAGE_ERROR(LogLevel.ERROR, "Fallo crítico al subir archivo al storage. ID: {}, Error: {}");

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