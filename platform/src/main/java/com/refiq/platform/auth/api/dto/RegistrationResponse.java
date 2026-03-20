package com.refiq.platform.auth.api.dto;


import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Data Transfer Object (DTO) representing a successful user registration response.
 *
 * @param message A human-readable message for the end-user indicating the result
 * (e.g., next steps or verification instructions).
 * @param userId  The universally unique identifier (UUID) assigned to the newly created user in the system.
 */
@Schema(description = "Respuesta de registro exitoso")
public record RegistrationResponse(

    @Schema(description = "Mensaje informativo sobre el resultado", example = "Usuario registrado correctamente.")
    String message,

    @Schema(description = "UUID asignado al usuario", example = "123e4567-e89b-12d3-a456-426614174000")
    String userId
) {

}