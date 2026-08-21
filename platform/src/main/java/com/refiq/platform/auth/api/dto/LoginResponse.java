package com.refiq.platform.auth.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Represents a successful login response.
 *
 * @param token The generated JSON Web Token (JWT) for the authenticated session.
 * @param type  The token type, which defaults to "Bearer".
 */
@Schema(description = "Successful authentication response")
public record LoginResponse(

    @Schema(description = "JWT access token", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
    String token,

    @Schema(description = "Token type", example = "Bearer", defaultValue = "Bearer")
    String type
) {

  public LoginResponse(String token){
    this(token, "Bearer");
  }
}