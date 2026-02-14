package com.refiq.platform.shared.observability;

import org.slf4j.Logger;

/**
 * Contract for structured log events following Data-Oriented Programming principles.
 * <p>
 * This interface is designed to be implemented by Enums (e.g., domain-specific log catalogs).
 * It centralizes the definition of log severity levels and message templates, ensuring consistent
 * observability across the platform and decoupling the log content from the service logic.
 * </p>
 */
public interface Loggable {

  /**
   * Supported log severity levels mapped to the underlying logging framework (SLF4J).
   */
  enum LogLevel {
    DEBUG, INFO, WARN, ERROR
  }

  LogLevel getLevel();

  String getMessageTemplate();

  /**
   * Executes the logging operation on the provided Logger instance.
   * <p>
   * This default method acts as a dispatcher, selecting the appropriate SLF4J method
   * (debug, info, warn, error) based on the event's {@link LogLevel}.
   * </p>
   *
   * @param logger The SLF4J {@link Logger} instance of the calling class.
   * @param params Variable arguments to replace placeholders in the message template.
   */
  default void log(Logger logger, Object... params) {
    switch (this.getLevel()) {
      case DEBUG -> logger.debug(this.getMessageTemplate(), params);
      case INFO -> logger.info(this.getMessageTemplate(), params);
      case WARN -> logger.warn(this.getMessageTemplate(), params);
      case ERROR -> logger.error(this.getMessageTemplate(), params);
    }
  }
}