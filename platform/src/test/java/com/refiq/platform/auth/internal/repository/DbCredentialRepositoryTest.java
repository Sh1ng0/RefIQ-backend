package com.refiq.platform.auth.internal.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.refiq.platform.auth.internal.domain.Credential;
import com.refiq.platform.support.slices.BasePostgresTest;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jooq.JooqTest;
import org.springframework.context.annotation.Import;

/**
 * Pruebas de integración puras para la persistencia DOP usando Testcontainers.
 * Se apoya en BasePostgresTest para heredar el Singleton de PostgreSQL.
 */
@JooqTest
@Import(DbCredentialRepository.class)
@DisplayName("Auth - DbCredentialRepository (jOOQ Integration)")
class DbCredentialRepositoryTest extends BasePostgresTest {

  @Autowired
  private DbCredentialRepository credentialRepository;

  @Test
  @DisplayName("Debe insertar una credencial inmutable y recuperarla con precisión")
  void shouldInsertAndFindCredential() {
    // GIVEN
    Credential newCredential = Credential.createNew("test@refiq.com", "hash_seguro");

    // WHEN
    credentialRepository.insert(newCredential);
    Optional<Credential> foundOpt = credentialRepository.findByEmail("test@refiq.com");

    // THEN
    assertThat(foundOpt).isPresent();

    Credential found = foundOpt.get();
    assertThat(found.id()).isEqualTo(newCredential.id());
    assertThat(found.email()).isEqualTo("test@refiq.com");
    assertThat(found.passwordHash()).isEqualTo("hash_seguro");

    // Verificamos que la zona horaria no se ha corrompido en el viaje a Postgres
    assertThat(found.createdAt()).isEqualTo(newCredential.createdAt());
  }

  @Test
  @DisplayName("existsByEmail debe devolver true si existe, false si no")
  void shouldReturnExistsCorrectly() {
    // GIVEN
    Credential credential = Credential.createNew("exists@refiq.com", "hash");
    credentialRepository.insert(credential);

    // WHEN / THEN
    assertThat(credentialRepository.existsByEmail("exists@refiq.com")).isTrue();
    assertThat(credentialRepository.existsByEmail("fantasma@refiq.com")).isFalse();
  }

  @Test
  @DisplayName("updatePassword debe alterar únicamente el hash y mantener la inmutabilidad de otros campos")
  void shouldUpdatePasswordSurgically() {
    // GIVEN
    Credential original = Credential.createNew("update@refiq.com", "old_hash");
    credentialRepository.insert(original);

    // Simulamos la transición de estado semántica en memoria
    Credential updatedState = original.updatePassword("new_hash");

    // WHEN
    credentialRepository.updatePassword(updatedState);

    // THEN
    Optional<Credential> foundOpt = credentialRepository.findByEmail("update@refiq.com");
    assertThat(foundOpt).isPresent();

    Credential fromDb = foundOpt.get();
    assertThat(fromDb.passwordHash()).isEqualTo("new_hash");
    assertThat(fromDb.id()).isEqualTo(original.id()); // El ID no cambia
    assertThat(fromDb.createdAt()).isEqualTo(original.createdAt()); // La fecha no cambia
  }
}