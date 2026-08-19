package com.refiq.platform.user.internal.repository;

import static com.refiq.platform.shared.db.generated.Tables.REFIQ_USER_PROFILES;

import com.refiq.platform.user.internal.domain.UserProfile;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementación Data-Oriented del repositorio de perfiles de usuario.
 * Sustituye a JpaRepository para recuperar el control absoluto sobre las consultas SQL.
 */
@Repository
public class DbUserProfileRepository {

  private final DSLContext jooq;

  public DbUserProfileRepository(DSLContext jooq) {
    this.jooq = jooq;
  }

  /**
   * Inserta un nuevo perfil en el sistema.
   * El record UserProfile ya viene con su estado completamente inicializado (id y createdAt)
   * desde la capa de servicio.
   */

  // COmentar en el javadoc la alineacion del Instant de Java con TIMESTAMP WITH TIME ZONE
  public void insert(UserProfile profile) {
    jooq.insertInto(REFIQ_USER_PROFILES)
        .set(REFIQ_USER_PROFILES.ID, profile.id())
        .set(REFIQ_USER_PROFILES.NAME, profile.name())
        .set(REFIQ_USER_PROFILES.CONTACT_EMAIL, profile.contactEmail())
        .set(REFIQ_USER_PROFILES.CREATED_AT, profile.createdAt().atOffset(ZoneOffset.UTC))
        .execute();
  }

  /**
   * Recupera un perfil de usuario por su identificador único.
   * jOOQ mapea las columnas seleccionadas directamente al constructor del Record.
   */
  public Optional<UserProfile> findById(UUID id) {
    return jooq.select(
            REFIQ_USER_PROFILES.ID,
            REFIQ_USER_PROFILES.NAME,
            REFIQ_USER_PROFILES.CONTACT_EMAIL,
            REFIQ_USER_PROFILES.CREATED_AT
        )
        .from(REFIQ_USER_PROFILES)
        .where(REFIQ_USER_PROFILES.ID.eq(id))
        .fetchOptionalInto(UserProfile.class);
  }


  // HELPER METHODS

  /**
   * Limpia la tabla. Exclusivo para tearDown de tests de integración.
   */
  public void deleteAll() {
    jooq.deleteFrom(com.refiq.platform.shared.db.generated.Tables.REFIQ_USER_PROFILES).execute();
  }
}