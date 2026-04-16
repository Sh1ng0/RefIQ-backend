package com.refiq.platform.user.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Datos públicos del perfil del usuario")
public record UserProfileResponse(

    @Schema(description = "ID único del usuario", example = "123e4567-e89b-12d3-a456-426614174000")
    UUID id,

    @Schema(description = "Nombre oficial del usuario", example = "Hospital Clinic")
    String name,

    @Schema(description = "Email de contacto principal", example = "contacto@clinic.com")
    String contactEmail,

    @Schema(description = "Fecha en la que el usuario se unió a RefIQ")
    Instant joinedAt
) {}