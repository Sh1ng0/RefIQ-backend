package com.refiq.platform.auth.api.dto.registration;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Represents a successful user registration response.
 *
 * @param message A human-readable message for the end-user indicating the result
 *                (e.g., next steps or verification instructions).
 * @param userId  The universally unique identifier (UUID) assigned to the newly created user in the system.
 */
@Schema(description = "Successful registration response")
public record RegistrationResponse(

    @Schema(description = "Informative message about the result", example = "User successfully registered.")
    String message,

    @Schema(description = "UUID assigned to the user", example = "123e4567-e89b-12d3-a456-426614174000")
    String userId
) {
}