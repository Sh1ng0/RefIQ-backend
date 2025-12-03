package com.refiq.platform.shared.observability;

import org.slf4j.Logger;

/**
 * Contrato para eventos de log estructurados (DOP style). Centraliza el formato y nivel de
 * severidad en Enums tipados.
 */
public interface Loggable {

  enum LogLevel {
    DEBUG, INFO, WARN, ERROR
  }

  LogLevel getLevel();

  String getMessageTemplate();

  default void log(Logger logger, Object... params) {
    switch (this.getLevel()) {
      case DEBUG -> logger.debug(this.getMessageTemplate(), params);
      case INFO -> logger.info(this.getMessageTemplate(), params);
      case WARN -> logger.warn(this.getMessageTemplate(), params);
      case ERROR -> logger.error(this.getMessageTemplate(), params);
    }
  }
}