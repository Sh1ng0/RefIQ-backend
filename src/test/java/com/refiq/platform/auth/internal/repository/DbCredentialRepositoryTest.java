package com.refiq.platform.auth.internal.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.refiq.platform.auth.internal.domain.Credential;
import com.refiq.platform.support.slices.BasePostgresTest;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
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
  @DisplayName("Should cleanly insert a new credential and retrieve it accurately")
  void shouldInsertAndFindCredential() {
    // GIVEN
    Credential newCredential = Credential.createNew("test@refiq.com", "secure_hash");

    // WHEN
    boolean isInserted = credentialRepository.tryInsert(newCredential);
    Optional<Credential> foundOpt = credentialRepository.findByEmail("test@refiq.com");

    // THEN
    assertThat(isInserted).isTrue(); // Validamos el contrato DOP
    assertThat(foundOpt).isPresent();

    Credential found = foundOpt.get();
    assertThat(found.id()).isEqualTo(newCredential.id());
    assertThat(found.email()).isEqualTo("test@refiq.com");
    assertThat(found.passwordHash()).isEqualTo("secure_hash");

    assertThat(found.createdAt())
        .isCloseTo(newCredential.createdAt(), within(1, ChronoUnit.MICROS));
  }

  @Test
  @DisplayName("tryInsert should return false without throwing exceptions on duplicate email")
  void shouldReturnFalseOnDuplicateEmail() {
    // GIVEN
    Credential firstCredential = Credential.createNew("collision@refiq.com", "hash_1");
    credentialRepository.tryInsert(firstCredential);

    // WHEN
    Credential secondCredential = Credential.createNew("collision@refiq.com", "hash_2");
    // Aquí es donde jOOQ y Postgres absorben el impacto gracias al ON CONFLICT DO NOTHING
    boolean isInserted = credentialRepository.tryInsert(secondCredential);

    // THEN
    assertThat(isInserted).isFalse(); // El flujo de control sigue intacto, devolvemos un dato

    // Verificamos que los datos del primer insert se mantuvieron inmutables en la BD
    Optional<Credential> foundOpt = credentialRepository.findByEmail("collision@refiq.com");
    assertThat(foundOpt).isPresent();
    assertThat(foundOpt.get().passwordHash()).isEqualTo("hash_1");
  }

  @Test
  @DisplayName("updatePassword should only alter the hash and maintain immutability of other fields")
  void shouldUpdatePassword() {
    // GIVEN
    Credential original = Credential.createNew("update@refiq.com", "old_hash");
    credentialRepository.tryInsert(original);

    Credential updatedState = original.updatePassword("new_hash");

    // WHEN
    credentialRepository.updatePassword(updatedState);

    // THEN
    Optional<Credential> foundOpt = credentialRepository.findByEmail("update@refiq.com");
    assertThat(foundOpt).isPresent();

    Credential fromDb = foundOpt.get();
    assertThat(fromDb.passwordHash()).isEqualTo("new_hash");
    assertThat(fromDb.id()).isEqualTo(original.id());

    assertThat(fromDb.createdAt())
        .isCloseTo(original.createdAt(), within(1, ChronoUnit.MICROS));
  }
}