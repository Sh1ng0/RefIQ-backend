package com.refiq.platform.auth.api.event;

import java.util.UUID;

/**
 * Evento de dominio emitido cuando una nueva credencial es creada exitosamente.
 * Sirve como contrato (Clave Foránea Lógica) entre el módulo Auth y el módulo User.
 */
public record UserRegisteredEvent(
    UUID accountId,
    String userName,
    String contactEmail
) {}