package com.refiq.platform.user.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "refiq_user_profiles")
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserProfile {

  // No GeneratedValue, id comes from auth's UUID
  @Id
  @Column(name = "id", updatable = false, nullable = false)
  private UUID id;

  @Column(nullable = false, length = 100)
  private String name;

  // Guardamos el email como contacto de facturación o notificaciones del hospital.
  @Column(name = "contact_email", nullable = false)
  private String contactEmail;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @PrePersist
  void onCreate() {
    this.createdAt = Instant.now();
  }
}