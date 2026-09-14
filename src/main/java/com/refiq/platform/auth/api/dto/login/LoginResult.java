package com.refiq.platform.auth.api.dto.login;

/**
 * Represents the exhaustive set of possible outcomes for a login operation.
 * <p>
 * By utilizing sealed interfaces, this construct enables exhaustive pattern matching at the
 * controller level. This ensures all security states are explicitly handled without relying on
 * exceptions for business logic flow control (Data-Oriented Programming).
 * </p>
 */
public sealed interface LoginResult {

  /**
   * Represents a successful authentication attempt.
   *
   * @param response The DTO containing the issued JWT and token details.
   */
  record Success(LoginResponse response) implements LoginResult {
  }

  /**
   * Represents a generic authentication failure.
   * <p>
   * For security reasons (specifically to mitigate user enumeration attacks),
   * this result intentionally obscures whether the failure was due to an
   * unknown email or an incorrect password.
   * </p>
   */
  record InvalidCredentials() implements LoginResult {
  }

  /**
   * Represents a temporary block due to rate limiting, designed to thwart brute-force attacks.
   *
   * @param message A message indicating that the maximum number of login attempts has been exceeded.
   */
  record TooManyRequests(String message) implements LoginResult {
  }
}