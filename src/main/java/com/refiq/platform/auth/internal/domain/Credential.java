package com.refiq.platform.auth.internal.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Core immutable representation of a user's access credentials in RefIQ.
 * <p>
 * Designed under Data-Oriented Programming principles: stateless, devoid of persistence
 * framework dependencies, and featuring explicit semantic state transitions.
 * </p>
 */
public record Credential(
    UUID id,
    String email,
    String passwordHash,
    Instant createdAt
) {

  /**
   * Canonical compact constructor.
   * <p>
   * Guarantees absolute data integrity at the moment of instantiation, regardless of whether
   * the data originates from the database or a new registration.
   * </p>
   */
  public Credential {
    Objects.requireNonNull(id, "Credential ID cannot be null");
    Objects.requireNonNull(email, "Email cannot be null");
    Objects.requireNonNull(passwordHash, "Password hash cannot be null");
    Objects.requireNonNull(createdAt, "Creation timestamp cannot be null");
  }

  /**
   * Static factory method for creating new credentials.
   * <p>
   * Generates identity and timestamps within the domain boundary prior to persistence.
   * </p>
   */
  public static Credential createNew(String email, String encodedPassword) {
    return new Credential(
        UUID.randomUUID(),
        email.toLowerCase().trim(),
        encodedPassword,
        Instant.now()
    );
  }

  /**
   * Semantic state transition.
   * <p>
   * Expresses a clear business intent and returns a new immutable snapshot of the state
   * with the updated password.
   * </p>
   */
  public Credential updatePassword(String newEncodedPassword) {
    if (newEncodedPassword == null || newEncodedPassword.isBlank()) {
      throw new IllegalArgumentException("The new password hash is invalid");
    }

    return new Credential(
        this.id,
        this.email,
        newEncodedPassword,
        this.createdAt
    );
  }

  /**
   * Overrides the standard record string representation.
   * <p>
   * Redacts the password hash to ensure it is never accidentally leaked into audit logs.
   * </p>
   */
  @Override
  public String toString() {
    return "Credential[" +
        "id=" + id + ", " +
        "email='" + email + '\'' + ", " +
        "createdAt=" + createdAt +
        ']';
  }
}