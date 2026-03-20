package com.refiq.platform.auth.api.dto;


import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Data Transfer Object (DTO) representing a successful login response.
 *
 * @param token The generated JSON Web Token (JWT) for the authenticated session.
 * @param type  The token type, which defaults to "Bearer".
 */
@Schema(description = "Respuesta de autenticación exitosa")
public record LoginResponse(

    @Schema(description = "Token JWT de acceso", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
    String token,

    @Schema(description = "Tipo de token", example = "Bearer", defaultValue = "Bearer")
    String type
) {


  public LoginResponse(String token){

    this(token, "Bearer");
  }
}
