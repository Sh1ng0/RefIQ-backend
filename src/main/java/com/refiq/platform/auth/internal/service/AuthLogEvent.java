package com.refiq.platform.auth.internal.service;

import com.refiq.platform.shared.observability.Loggable;

public enum AuthLogEvent implements Loggable {

  // --- FLOW TRACES ---
  PROCESSING_REGISTRATION_REQUEST(LogLevel.DEBUG, "Procesando solicitud de registro para: {} desde IP: {}"),
  PROCESSING_LOGIN_REQUEST(LogLevel.DEBUG, "Procesando solicitud de login para: {}"),

  // --- REGISTER ---
  USER_REGISTERED(LogLevel.INFO, "Nuevo usuario registrado exitosamente. Email: {}, ID: {}"),
  REGISTRATION_FAILED_EMAIL_EXISTS(LogLevel.WARN, "Intento de registro fallido. El email ya existe: {}"),
  REGISTRATION_BLOCKED_RATE_LIMIT(LogLevel.WARN, "Registro bloqueado por Rate Limiting (Posible Bot). IP: {}"),

  // --- LOGIN ---
  LOGIN_SUCCESS(LogLevel.INFO, "Usuario autenticado correctamente. ID: {}"),
  LOGIN_FAILED_INVALID_CREDENTIALS(LogLevel.WARN, "Intento de login fallido para el email: {}"),
  LOGIN_BLOCKED_RATE_LIMIT(LogLevel.WARN, "Bloqueo por Rate Limiting para el email: {}"),

  // --- JWT / SECURITY ---
  JWT_VALIDATION_FAILED(LogLevel.DEBUG, "Token JWT inválido, malformado o expirado. Detalle: {}"),
  LOGOUT_SUCCESS(LogLevel.INFO, "Usuario ha cerrado sesión voluntariamente. ID: {}");

  private final LogLevel level;
  private final String template;

  AuthLogEvent(LogLevel level, String template) {
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