package com.refiq.platform.user.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * Represents the public profile data of a user.
 */
@Schema(description = "Public data of the user profile")
public record UserProfileResponse(

    @Schema(description = "Unique user ID", example = "123e4567-e89b-12d3-a456-426614174000")
    UUID id,

    @Schema(description = "Official name of the user or hospital", example = "Hospital Clinic")
    String name,

    @Schema(description = "Primary contact email", example = "contact@clinic.com")
    String contactEmail,

    @Schema(description = "Timestamp of when the user joined RefIQ")
    Instant joinedAt
) {}