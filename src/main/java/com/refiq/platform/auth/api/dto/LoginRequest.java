package com.refiq.platform.auth.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Contains the client's credentials for authentication.
 */
@Schema(description = "Payload containing user access credentials")
public record LoginRequest(

    @Schema(description = "User's email address", example = "user@refiq.com")
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    String email,

    @Schema(description = "User's password", example = "P@ssw0rd123!", maxLength = 128)
    @NotBlank(message = "Password is required")
    @Size(max = 128, message = "Password exceeds the maximum allowed length") // The lifesaver patch
    String password
) {
}