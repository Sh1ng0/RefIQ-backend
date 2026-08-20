package com.refiq.platform.user.internal.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Pure domain record representing a User Profile.
 * Immutable, stateless, and completely decoupled from JPA.
 */
public record UserProfile(
    UUID id,
    String name,
    String contactEmail,
    Instant createdAt
) {

  /**
   * Factory method para instanciar un nuevo perfil a partir de un evento.
   * Centraliza la lógica de inicialización (como el createdAt) que antes
   * delegábamos mágicamente en el @PrePersist de JPA.
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