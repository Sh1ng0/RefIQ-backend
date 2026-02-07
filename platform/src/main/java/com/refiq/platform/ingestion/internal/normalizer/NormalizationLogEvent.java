package com.refiq.platform.ingestion.internal.normalizer;

import com.refiq.platform.shared.observability.Loggable;

/**
 * Eventos de logging específicos de la lógica de normalización de CSV.
 */
public enum NormalizationLogEvent implements Loggable {

  ROW_STRUCTURE_INVALID(LogLevel.DEBUG, "Fila rechazada por estructura inválida. Columnas esperadas: ~6, Encontradas: {}. Contenido raw: {}"),

  VALUE_NOT_NUMERIC(LogLevel.WARN, "Valor de resultado no numérico rechazado. Valor: '{}' | Fila Raw: {}"),

  UNEXPECTED_PARSING_ERROR(LogLevel.ERROR, "Excepción no controlada normalizando fila. Error: {} | Fila: {}");

  private final LogLevel level;
  private final String template;

  NormalizationLogEvent(LogLevel level, String template) {
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