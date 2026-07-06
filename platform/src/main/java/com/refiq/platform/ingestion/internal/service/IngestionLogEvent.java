package com.refiq.platform.ingestion.internal.service;

import com.refiq.platform.shared.observability.Loggable;

/**
 * Event log catalog for the ingestion service (Data Lake routing).
 */
enum IngestionLogEvent implements Loggable {

  // --- Fase 1: Recepción ---
  UPLOAD_INITIATED(LogLevel.INFO, "Iniciando ingesta hacia Data Lake. Archivo: '{}', Analito: '{}', Tamaño: {} bytes"),

  // --- Fase 2: Streaming a S3 (Bronze) ---
  BRONZE_PROCESSING_STARTED(LogLevel.DEBUG, "Iniciando streaming hacia capa Bronze. Destino S3: {}"),
  MULTIPART_INITIATED(LogLevel.DEBUG, "Transacción S3 Multipart iniciada. UploadId: {}"),
  MULTIPART_PART_UPLOADED(LogLevel.DEBUG, "Parte #{} subida exitosamente. ETag: {}"),
  MULTIPART_PART_FAILED(LogLevel.ERROR, "Fallo subiendo chunk #{}. Error: {}"),

  // --- Resultados ---
  BRONZE_SUMMARY(LogLevel.INFO, "Ingesta Bronze finalizada con éxito [FileID: {}]. Chunks procesados: {}"),

  // --- Errores Globales ---
  PIPELINE_ERROR(LogLevel.ERROR, "Fallo crítico en pipeline de ingesta [FileID: {}]. Causa: {}"),
  MULTIPART_ABORTED(LogLevel.ERROR, "Transacción Multipart abortada por error crítico. Key: {}"),

  // --- Trigger Data Lake ---
  DATALAKE_TRIGGER_SENT(LogLevel.INFO, "Data Lake notificado correctamente vía HTTP para iniciar pipeline."),
  DATALAKE_TRIGGER_FAILED(LogLevel.WARN, "Fallo al notificar al Data Lake. Archivo en S3, pero pipeline no inició: {}");

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