package com.refiq.platform.auth.api.dto;


/**
 * Data Transfer Object (DTO) representing a successful user registration response.
 *
 * @param message A human-readable message for the end-user indicating the result
 * (e.g., next steps or verification instructions).
 * @param userId  The universally unique identifier (UUID) assigned to the newly created user in the system.
 */
public record RegistrationResponse(
    String message,
    String userId
) {

}