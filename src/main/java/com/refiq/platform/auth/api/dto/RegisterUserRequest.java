package com.refiq.platform.auth.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Represents a new user registration request.
 * <p>
 * Incorporates necessary validation constraints to ensure the integrity and security
 * of user credentials before processing by the service layer.
 * </p>
 *
 * @param name     The name of the hospital or laboratory.
 * @param email    The user's email address. Must adhere to a valid email format.
 * @param password The user's chosen password. Must comply with the strict security policy:
 *                 minimum of 8 characters, containing at least one uppercase letter,
 *                 one lowercase letter, one digit, and one special character (@#$%^&+=!).
 */
@Schema(description = "Payload for registering a new user/hospital")
public record RegisterUserRequest(

    @Schema(description = "Name of the hospital or laboratory", example = "Hospital Clinic")
    @NotBlank(message = "Hospital name is required")
    @Size(max = 100, message = "Hospital name cannot exceed 100 characters")
    String name,

    @Schema(description = "User's email address", example = "user@refiq.com")
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    String email,

    @Schema(
        description = "Robust user password",
        example = "P@ssw0rd123!",
        pattern = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=!])(?=\\S+$).{8,}$"
    )
    @NotBlank
    @Pattern(regexp = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=!])(?=\\S+$).{8,}$",
        message = "Password must be robust (min 8 characters, uppercase, number, and symbol)")
    String password
) {
}