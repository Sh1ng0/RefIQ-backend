package com.refiq.platform.calculation.internal.service;


import com.refiq.platform.shared.observability.Loggable;

public enum CalculationTrackingLogEvent implements Loggable {

  // --- Ingestion Success Tracking ---
  TRACKING_EVENT_RECEIVED(LogLevel.DEBUG,
      "Recibido evento de nueva ingesta (Modulith) para archivo: {}"),
  TRACKING_RECORD_CREATED(LogLevel.INFO,
      "Caja de seguimiento PENDING creada en BD para archivo: {}"),
  TRACKING_ALREADY_HANDLED(
      LogLevel.INFO,
      "Cálculo no reclamado para fileId: {}. Estado actual: {}"
  ),

  // --- Ingestion Failure Tracking ---
  INGESTION_FAILED_EVENT_RECEIVED(LogLevel.WARN,
      "Recibido evento de fallo de ingesta para archivo: {}. Motivo: {}"),
  TRACKING_RECORD_UPDATED_TO_FAILED(LogLevel.INFO,
      "Estado actualizado a FAILED en BD tras fallo de ingesta para archivo: {}"),

  // --- Common Tracking ---
  TRACKING_RECORD_NOT_FOUND(LogLevel.ERROR,
      "¡CUIDADO! No se encontró la entidad de seguimiento para el fileId: {}");

  private final LogLevel level;
  private final String messageTemplate;

  CalculationTrackingLogEvent(LogLevel level, String messageTemplate) {
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