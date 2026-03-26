package com.refiq.platform.auth.api.dto;



import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Data Transfer Object (DTO) containing the client's credentials for authentication.
 */
@Schema(description = "Payload con las credenciales de acceso del usuario")
public record LoginRequest(

    @Schema(description = "Correo electrónico del usuario", example = "usuario@refiq.com")
    @NotBlank(message = "El email es obligatorio")
    @Email(message = "El formato del email no es válido")
    String email,

    @Schema(description = "Contraseña del usuario", example = "P@ssw0rd123!", maxLength = 128)
    @NotBlank(message = "La contraseña es obligatoria")
    @Size(max = 128, message = "La contraseña excede la longitud máxima permitida") // El parche salvador
    String password
) {
}