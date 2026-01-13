package com.refiq.platform.calculation.internal.service;

import com.refiq.platform.shared.observability.Loggable;

public enum CalculationLogEvent implements Loggable {

  CALCULATION_STARTED(LogLevel.INFO, "Iniciando cálculo de rangos para archivo: {} con percentiles [{}, {}]"),
  PLUMBER_REQUEST_SENT(LogLevel.DEBUG, "Enviando petición a Plumber para la key S3: {}"),
  CALCULATION_COMPLETED(LogLevel.INFO, "Cálculo finalizado exitosamente para test: {}"),
  PLUMBER_ERROR(LogLevel.ERROR, "Error en el motor de R (Plumber): {}");

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