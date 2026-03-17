package com.refiq.platform.auth.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;


/**
 * Data Transfer Object (DTO) representing a new user registration request.
 * <p>
 * Incorporates necessary validation constraints to ensure the integrity and security
 * of user credentials before processing by the service layer.
 * </p>
 *
 * @param email    The user's email address. Must adhere to a valid email format.
 * @param password The user's chosen password. Must comply with the strict security policy:
 * minimum of 8 characters, containing at least one uppercase letter,
 * one lowercase letter, one digit, and one special character (@#$%^&+=!).
 */
public record RegisterUserRequest(

    @NotBlank(message = "El email es obligatorio")
    @Email(message = "El formato del email no es válido")
    String email,

    @NotBlank
    @Pattern(regexp = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=!])(?=\\S+$).{8,}$",
        message = "La contraseña debe ser robusta (min 8 caracteres, mayúscula, número y símbolo)")
    String password
) {

}