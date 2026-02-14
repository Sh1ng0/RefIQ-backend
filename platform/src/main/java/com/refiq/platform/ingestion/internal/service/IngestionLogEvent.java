package com.refiq.platform.ingestion.internal.service;



import com.refiq.platform.shared.observability.Loggable;

/**
 * Event log cataog for the ingestion service
 */
enum IngestionLogEvent implements Loggable {

  // --- FAse 1: Recepción y RAW ---
  UPLOAD_INITIATED(LogLevel.DEBUG, "Iniciando proceso de ingesta. Archivo: '{}', Tamaño: {} bytes"),
  RAW_UPLOAD_STARTED(LogLevel.DEBUG, "Subiendo respaldo RAW a S3. Key: {}"),
  RAW_UPLOAD_SUCCESS(LogLevel.INFO, "Respaldo RAW persistido correctamente. Key: {}"),

  // --- Fase 2: Procesamiento Canónico ---
  SEPARATOR_DETECTED(LogLevel.DEBUG, "Separador CSV detectado para '{}': '{}'"),
  SEPARATOR_DETECTION_FAILED(LogLevel.WARN, "Fallo en heurística de separador. Usando default ';'. Causa: {}"),

  CANONICAL_PROCESSING_STARTED(LogLevel.DEBUG, "Iniciando normalización y carga Canónica. Destino: {}"),
  MULTIPART_INITIATED(LogLevel.DEBUG, "Transacción S3 Multipart iniciada. UploadId: {}"),
  MULTIPART_PART_UPLOADED(LogLevel.DEBUG, "Parte #{} subida exitosamente. ETag: {}"),
  MULTIPART_PART_FAILED(LogLevel.ERROR, "Fallo subiendo parte #{}. Error: {}"),

  // --- Resultados de Normalización ---
  ROW_REJECTED(LogLevel.WARN, "Fila descartada. Motivo: {} | Data: {}"),
  CANONICAL_SUMMARY(LogLevel.INFO, "Ingesta Canónica finalizada [FileID: {}]. Total: {}, Válidas: {}, Rechazadas: {}"),

  // --- Errores Globales ---
  PIPELINE_ERROR(LogLevel.ERROR, "Fallo crítico en pipeline de ingesta [FileID: {}]. Causa: {}"),
  MULTIPART_ABORTED(LogLevel.ERROR, "Transacción Multipart abortada por error crítico. Key: {}");



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