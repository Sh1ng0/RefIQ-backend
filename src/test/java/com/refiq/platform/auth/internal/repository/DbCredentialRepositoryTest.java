package com.refiq.platform.auth.internal.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.refiq.platform.auth.internal.domain.Credential;
import com.refiq.platform.support.slices.BasePostgresTest;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jooq.JooqTest;
import org.springframework.context.annotation.Import;

/**
 * Pure integration tests for DOP persistence using Testcontainers.
 * Relies on BasePostgresTest to inherit the PostgreSQL Singleton.
 */
@JooqTest
@Import(DbCredentialRepository.class)
@DisplayName("Auth - DbCredentialRepository (jOOQ Integration)")
class DbCredentialRepositoryTest extends BasePostgresTest {

  @Autowired
  private DbCredentialRepository credentialRepository;

  @Test
  @DisplayName("Should insert an immutable credential and retrieve it accurately")
  void shouldInsertAndFindCredential() {
    // GIVEN
    Credential newCredential = Credential.createNew("test@refiq.com", "secure_hash");

    // WHEN
    credentialRepository.insert(newCredential);
    Optional<Credential> foundOpt = credentialRepository.findByEmail("test@refiq.com");

    // THEN
    assertThat(foundOpt).isPresent();

    Credential found = foundOpt.get();
    assertThat(found.id()).isEqualTo(newCredential.id());
    assertThat(found.email()).isEqualTo("test@refiq.com");
    assertThat(found.passwordHash()).isEqualTo("secure_hash");

    assertThat(found.createdAt().truncatedTo(ChronoUnit.MICROS))
        .isEqualTo(newCredential.createdAt().truncatedTo(ChronoUnit.MICROS));
  }

  @Test
  @DisplayName("existsByEmail should return true if it exists, false otherwise")
  void shouldReturnExistsCorrectly() {
    // GIVEN
    Credential credential = Credential.createNew("exists@refiq.com", "hash");
    credentialRepository.insert(credential);

    // WHEN / THEN
    assertThat(credentialRepository.existsByEmail("exists@refiq.com")).isTrue();
    assertThat(credentialRepository.existsByEmail("phantom@refiq.com")).isFalse();
  }

  @Test
  @DisplayName("updatePassword should only alter the hash and maintain immutability of other fields")
  void shouldUpdatePassword() {
    // GIVEN
    Credential original = Credential.createNew("update@refiq.com", "old_hash");
    credentialRepository.insert(original);

    Credential updatedState = original.updatePassword("new_hash");

    // WHEN
    credentialRepository.updatePassword(updatedState);

    // THEN
    Optional<Credential> foundOpt = credentialRepository.findByEmail("update@refiq.com");
    assertThat(foundOpt).isPresent();

    Credential fromDb = foundOpt.get();
    assertThat(fromDb.passwordHash()).isEqualTo("new_hash");
    assertThat(fromDb.id()).isEqualTo(original.id());
    assertThat(fromDb.createdAt().truncatedTo(ChronoUnit.MICROS))
        .isEqualTo(original.createdAt().truncatedTo(ChronoUnit.MICROS));
  }
}
