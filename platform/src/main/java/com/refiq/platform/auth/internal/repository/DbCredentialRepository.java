package com.refiq.platform.auth.internal.repository;

import com.refiq.platform.auth.internal.domain.Credential;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.time.ZoneOffset;
import java.util.Optional;


import static com.refiq.platform.shared.db.generated.Tables.REFIQ_CREDENTIALS;


/**
 * Implementación Data-Oriented del repositorio de credenciales.
 * Utiliza jOOQ para garantizar consultas tipadas y transiciones de estado explícitas,
 * devolviendo y recibiendo únicamente records inmutables del dominio.
 */
@Repository
public class DbCredentialRepository {

  private final DSLContext jooq;

  public DbCredentialRepository(DSLContext jooq) {
    this.jooq = jooq;
  }

  /**
   * Verifica la existencia de un email mediante una consulta EXISTS optimizada.
   * Reemplaza la antigua magia de Spring Data JPA con un SQL predecible.
   */
  public boolean existsByEmail(String email) {
    return jooq.fetchExists(
        jooq.selectOne()
            .from(REFIQ_CREDENTIALS)
            .where(REFIQ_CREDENTIALS.EMAIL.eq(email))
    );
  }

  /**
   * Recupera una credencial pura desde la base de datos.
   * Seleccionamos estrictamente las columnas necesarias y delegamos en jOOQ
   * el mapeo directo al constructor canónico del record Credential.
   */
  public Optional<Credential> findByEmail(String email) {
    return jooq.select(
            REFIQ_CREDENTIALS.ID,
            REFIQ_CREDENTIALS.EMAIL,
            REFIQ_CREDENTIALS.PASSWORD_HASH,
            REFIQ_CREDENTIALS.CREATED_AT
        )
        .from(REFIQ_CREDENTIALS)
        .where(REFIQ_CREDENTIALS.EMAIL.eq(email))
        .fetchOptionalInto(Credential.class);
  }

  /**
   * Inserta un nuevo hecho (credencial) en el sistema.
   * Al no existir @PrePersist ni @GeneratedValue, el record entrante
   * ya es dueño de su ID y su Timestamp.
   */
  public void insert(Credential credential) {
    jooq.insertInto(REFIQ_CREDENTIALS)
        .set(REFIQ_CREDENTIALS.ID, credential.id())
        .set(REFIQ_CREDENTIALS.EMAIL, credential.email())
        .set(REFIQ_CREDENTIALS.PASSWORD_HASH, credential.passwordHash())
        // Alineamos explícitamente el Instant de Java con el TIMESTAMP WITH TIME ZONE de Postgres
        .set(REFIQ_CREDENTIALS.CREATED_AT, credential.createdAt().atOffset(ZoneOffset.UTC))
        .execute();
  }

  /**
   * Transición de estado quirúrgica.
   * A diferencia del save() de JPA, este método comunica explícitamente
   * la intención de alterar únicamente el hash de la contraseña de una credencial existente.
   */
  public void updatePassword(Credential credential) {
    jooq.update(REFIQ_CREDENTIALS)
        .set(REFIQ_CREDENTIALS.PASSWORD_HASH, credential.passwordHash())
        .where(REFIQ_CREDENTIALS.ID.eq(credential.id()))
        .execute();
  }
}