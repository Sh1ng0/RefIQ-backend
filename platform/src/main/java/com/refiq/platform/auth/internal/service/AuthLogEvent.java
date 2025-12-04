package com.refiq.platform.auth.internal.service;

import com.refiq.platform.shared.observability.Loggable;

enum AuthLogEvent implements Loggable {

    USER_REGISTERED(LogLevel.INFO, "Nuevo usuario registrado exitosamente. Email: {}, ID: {}"),
    REGISTRATION_FAILED_EMAIL_EXISTS(LogLevel.WARN, "Intento de registro fallido. El email ya existe: {}");

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