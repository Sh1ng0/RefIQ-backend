package com.refiq.platform.user.internal.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Pure domain record representing a user profile.
 * <p>
 * Designed as an immutable and stateless structure.
 * </p>
 */
public record UserProfile(
    UUID id,
    String name,
    String contactEmail,
    Instant createdAt
) {

  /**
   * Instantiates a new user profile from an external event.
   * <p>
   * Centralizes initialization logic, such as timestamp generation, within the
   * domain boundary prior to persistence.
   * </p>
   */
  public static UserProfile createNew(UUID id, String name, String contactEmail) {
    return new UserProfile(
        id,
        name,
        contactEmail,
        Instant.now()
    );
  }
}