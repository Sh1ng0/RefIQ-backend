package com.refiq.platform.auth.api.dto;


/**
 * A sealed interface representing the exhaustive set of possible outcomes for a user registration operation.
 * <p>
 * By utilizing a sealed hierarchy, this construct enables safe and exhaustive pattern matching
 * at the controller level. This Data-Oriented Programming (DOP) approach eliminates the need
 * to use exceptions for expected business logic deviations, such as attempting to register
 * with an email address that is already in use.
 * </p>
 */
public sealed interface RegistrationResult {

  /**
   * Represents a successfully completed registration process.
   *
   * @param response The confirmation data and details intended for the client.
   */
  record Success(RegistrationResponse response) implements RegistrationResult {

  }

  /**
   * Represents a registration failure caused by a conflicting email address that already exists in the system.
   *
   * @param email The conflicting email address that triggered the failure.
   */
  record EmailAlreadyExists(String email) implements RegistrationResult {

  }
}