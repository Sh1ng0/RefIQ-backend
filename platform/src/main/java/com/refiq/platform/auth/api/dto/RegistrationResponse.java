package com.refiq.platform.auth.api.dto;


/**
 * DTO de respuesta exitosa tras el registro de un usuario.
 *
 * @param message Mensaje legible para el usuario final indicando el resultado (ej. instrucciones de
 *                verificación).
 * @param userId  Identificador único (UUID) asignado al usuario recién creado en el sistema.
 */
public record RegistrationResponse(
    String message,
    String userId
) {

}