package com.refiq.platform.modules.auth.internal;


import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity
@Table(name = "refiq_credentials")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class Credential { // Package-private: Spring Modulith magic happens here.

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String passwordHash;


    @Column(name = "created_at", nullable = false, updatable = false)
    private java.time.Instant createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = java.time.Instant.now();
    }
}