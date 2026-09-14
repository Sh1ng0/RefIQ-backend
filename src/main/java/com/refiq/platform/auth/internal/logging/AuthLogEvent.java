package com.refiq.platform.auth.internal.logging;

import org.slf4j.Logger;
import java.util.UUID;

/**
 * Domain-specific structured log events for the AuthService.
 * Implemented as a sealed interface to guarantee exhaustive pattern matching.
 * Placed in an internal logging package to be shared across auth submodules
 * while protected by Spring Modulith from external access.
 */
public sealed interface AuthLogEvent {

  // --- FLOW TRACES ---
  record ProcessingRegistrationRequest(String email, String ipAddress) implements AuthLogEvent {}
  record ProcessingLoginRequest(String email) implements AuthLogEvent {}

  // --- REGISTER ---
  record UserRegistered(String email, UUID userId) implements AuthLogEvent {}
  record RegistrationFailedEmailExists(String email) implements AuthLogEvent {}
  record RegistrationBlockedRateLimit(String ipAddress) implements AuthLogEvent {}

  // --- LOGIN ---
  record LoginSuccess(UUID userId) implements AuthLogEvent {}
  record LoginFailedInvalidCredentials(String email) implements AuthLogEvent {}
  record LoginBlockedRateLimit(String email) implements AuthLogEvent {}

  // --- JWT / SECURITY ---
  record JwtValidationFailed(String detail) implements AuthLogEvent {}
  record LogoutSuccess(UUID userId) implements AuthLogEvent {}

  default void log(Logger logger) {
    switch (this) {
      case ProcessingRegistrationRequest e -> logger.atDebug()
          .setMessage("Processing registration request")
          .addKeyValue("email", e.email())
          .addKeyValue("ip_address", e.ipAddress())
          .log();

      case ProcessingLoginRequest e -> logger.atDebug()
          .setMessage("Processing login request")
          .addKeyValue("email", e.email())
          .log();

      case UserRegistered e -> logger.atInfo()
          .setMessage("New user successfully registered")
          .addKeyValue("email", e.email())
          .addKeyValue("user_id", e.userId())
          .log();

      case RegistrationFailedEmailExists e -> logger.atWarn()
          .setMessage("Registration attempt failed. Email already exists")
          .addKeyValue("email", e.email())
          .log();

      case RegistrationBlockedRateLimit e -> logger.atWarn()
          .setMessage("Registration blocked by rate limiting (Possible bot)")
          .addKeyValue("ip_address", e.ipAddress())
          .log();

      case LoginSuccess e -> logger.atInfo()
          .setMessage("User successfully authenticated")
          .addKeyValue("user_id", e.userId())
          .log();

      case LoginFailedInvalidCredentials e -> logger.atWarn()
          .setMessage("Login attempt failed due to invalid credentials")
          .addKeyValue("email", e.email())
          .log();

      case LoginBlockedRateLimit e -> logger.atWarn()
          .setMessage("Login blocked by rate limiting")
          .addKeyValue("email", e.email())
          .log();

      case JwtValidationFailed e -> logger.atDebug()
          .setMessage("Invalid, malformed, or expired JWT token")
          .addKeyValue("detail", e.detail())
          .log();

      case LogoutSuccess e -> logger.atInfo()
          .setMessage("User successfully logged out")
          .addKeyValue("user_id", e.userId())
          .log();
    }
  }
}