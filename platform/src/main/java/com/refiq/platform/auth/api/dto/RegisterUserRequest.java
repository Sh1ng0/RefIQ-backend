package com.refiq.platform.auth.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;


/**
 * DTO que representa la solicitud de registro de un nuevo usuario.
 * <p>
 * Contiene las validaciones de formato necesarias para garantizar la integridad y seguridad de las
 * credenciales antes de procesarlas.
 *
 * @param email    Correo electrónico del usuario. Debe tener un formato válido.
 * @param password Contraseña del usuario. Debe cumplir con la política de seguridad: mínimo 8
 *                 caracteres, al menos una mayúscula, una minúscula, un número y un carácter
 *                 especial (@#$%^&+=!).
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