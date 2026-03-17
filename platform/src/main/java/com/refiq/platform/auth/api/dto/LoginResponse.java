package com.refiq.platform.auth.api.dto;


/**
 * Data Transfer Object (DTO) representing a successful login response.
 *
 * @param token The generated JSON Web Token (JWT) for the authenticated session.
 * @param type  The token type, which defaults to "Bearer".
 */
public record LoginResponse(
    String token,
    String type // Quizá un enum, veremos cómo respira
) {


  public LoginResponse(String token){

    this(token, "Bearer");
  }
}
