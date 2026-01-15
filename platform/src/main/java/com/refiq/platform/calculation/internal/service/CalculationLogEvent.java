package com.refiq.platform.calculation.internal.service;

import com.refiq.platform.shared.observability.Loggable;

public enum CalculationLogEvent implements Loggable {

  CALCULATION_STARTED(LogLevel.INFO, "Iniciando cálculo de rangos para archivo: {} con percentiles [{}, {}]"),
  PLUMBER_REQUEST_SENT(LogLevel.DEBUG, "Enviando petición a Plumber para la key S3: {}"),
  CALCULATION_COMPLETED(LogLevel.INFO, "Cálculo finalizado exitosamente para test: {}"),
  PLUMBER_ERROR(LogLevel.ERROR, "Error en el motor de R (Plumber): {}"),
  ANALYSIS_INITIATED(LogLevel.DEBUG, "Enviando petición a R Plumber para S3Key: {}"),

  R_RESPONSE_RECEIVED(LogLevel.DEBUG, "JSON crudo recibido de R: {}"),

  R_DESERIALIZATION_ERROR(LogLevel.ERROR, "Error crítico deserializando respuesta de R. JSON: {}"),

  R_TECHNICAL_ERROR(LogLevel.ERROR, "El motor R devolvió un error HTTP. Status: {}");

  // Problema con s3 maybe
  private final LogLevel level;
  private final String messageTemplate;

  CalculationLogEvent(LogLevel level, String messageTemplate) {
    this.level = level;
    this.messageTemplate = messageTemplate;
  }

  @Override public LogLevel getLevel() { return level; }
  @Override public String getMessageTemplate() { return messageTemplate; }
}