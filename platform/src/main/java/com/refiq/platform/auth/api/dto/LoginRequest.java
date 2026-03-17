package com.refiq.platform.auth.api.dto;



import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Data Transfer Object (DTO) containing the client's credentials for authentication.
 */
public record LoginRequest(

    @NotBlank(message = "El email es obligatorio")
    @Email(message = "El formato del email no es válido")
    String email,

    @NotBlank(message = "La contraseña es obligatoria")
    @Size(max = 128, message = "La contraseña excede la longitud máxima permitida") // El parche salvador
    String password
) {
}