package com.refiq.platform.calculation.internal.service;

import com.refiq.platform.shared.observability.Loggable;

public enum CalculationLogEvent implements Loggable {

  CALCULATION_STARTED(LogLevel.INFO,
      "Iniciando cálculo de rangos para archivo: {} con percentiles [{}, {}]"),
  PLUMBER_REQUEST_SENT(LogLevel.DEBUG, "Enviando petición a Plumber para la key S3: {}"),
  CALCULATION_COMPLETED(LogLevel.INFO, "Cálculo finalizado exitosamente para test: {}"),
  PLUMBER_ERROR(LogLevel.ERROR, "Error en el motor de R (Plumber): {}"),
  ANALYSIS_INITIATED(LogLevel.DEBUG, "Enviando petición a R Plumber para S3Key: {}"),

  R_RESPONSE_RECEIVED(LogLevel.DEBUG, "JSON crudo recibido de R: {}"),
  R_DESERIALIZATION_ERROR(LogLevel.ERROR, "Error crítico deserializando respuesta de R. JSON: {}"),
  R_TECHNICAL_ERROR(LogLevel.ERROR, "El motor R devolvió un error HTTP. Status: {}"),

  // --- MinIO Webhooks ---
  MINIO_WEBHOOK_RECEIVED(LogLevel.INFO, "Webhook de MinIO recibido para archivo Gold: {}"),
  MINIO_WEBHOOK_PARSING_WARN(LogLevel.WARN,
      "No se pudo extraer el código del analito de la ruta: {}"),
  MINIO_WEBHOOK_ERROR(LogLevel.ERROR, "Error inesperado procesando el webhook de MinIO: {}"),
  MINIO_WEBHOOK_DUPLICATE_IGNORED(LogLevel.INFO,
      "Webhook duplicado ignorado para analito {}. Estado en BD: {}"),

  // --- Calculation Results Handling ---
  CALCULATION_SUCCESS(LogLevel.INFO, "Cálculo exitoso para {}. Rango: {}"),
  CALCULATION_DATA_INCONSISTENCY(LogLevel.WARN, "Inconsistencia en datos para {}: {}"),
  CALCULATION_ENGINE_UNAVAILABLE(LogLevel.ERROR, "Motor R no disponible para {}: {}"),
  CALCULATION_INVALID_REQUEST(LogLevel.ERROR, "Petición inválida generada por el Webhook: {}"),

  // --- Persistence ---
  CALCULATION_RESULT_SAVED(LogLevel.INFO, "Cálculo exitoso. JSON guardado en BD para fileId: {}"),

  // --- Inconsistencias de Negocio ---
  DATA_INCONSISTENCY_TRACKED(LogLevel.WARN,
      "Inconsistencia de datos detectada y registrada [FileID: {}]. Detalles: {}");

  private final LogLevel level;
  private final String messageTemplate;

  CalculationLogEvent(LogLevel level, String messageTemplate) {
    this.level = level;
    this.messageTemplate = messageTemplate;
  }

  @Override
  public LogLevel getLevel() {
    return level;
  }

  @Override
  public String getMessageTemplate() {
    return messageTemplate;
  }
}