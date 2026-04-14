package com.refiq.platform.user.internal.service;

import com.refiq.platform.shared.observability.Loggable;

public enum UserLogEvent implements Loggable {

  PROFILE_CREATED(LogLevel.INFO, "Perfil de hospital creado exitosamente. ID: {}, Nombre: {}"),
  PROFILE_RETRIEVED(LogLevel.DEBUG, "Perfil consultado. ID: {}"),
  PROFILE_NOT_FOUND(LogLevel.WARN, "Intento de consulta de un perfil inexistente. ID: {}");

  private final LogLevel level;
  private final String template;

  UserLogEvent(LogLevel level, String template) {
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