package com.refiq.platform.user.internal.repository;

import static com.refiq.platform.shared.db.generated.Tables.REFIQ_USER_PROFILES;

import com.refiq.platform.user.internal.domain.UserProfile;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

/**
 * Data-oriented implementation of the user profile repository.
 * <p>
 * Utilizes jOOQ to ensure type-safe SQL queries and explicit state management,
 * returning only pure domain records.
 * </p>
 */
@Repository
public class DbUserProfileRepository {

  private final DSLContext dsl;

  public DbUserProfileRepository(DSLContext dsl) {
    this.dsl = dsl;
  }



  /**
   * Idempotently inserts a new user profile into the system.
   * <p>
   * Uses ON CONFLICT DO NOTHING to guarantee safe retries (at-least-once delivery)
   * when consuming domain events. Assumes the profile record is fully initialized
   * with its identifier and creation timestamp.
   * </p>
   */
  public void insert(UserProfile profile) {
    dsl.insertInto(REFIQ_USER_PROFILES)
        .set(REFIQ_USER_PROFILES.ID, profile.id())
        .set(REFIQ_USER_PROFILES.NAME, profile.name())
        .set(REFIQ_USER_PROFILES.CONTACT_EMAIL, profile.contactEmail())
        .set(REFIQ_USER_PROFILES.CREATED_AT, profile.createdAt().atOffset(ZoneOffset.UTC))
        .onConflict(REFIQ_USER_PROFILES.ID)
        .doNothing()
        .execute();
  }

  /**
   * Retrieves a user profile by its unique identifier.
   * <p>
   * Uses explicit mapping to the domain record's constructor to ensure absolute
   * compile-time type safety, avoiding runtime reflection blind spots.
   * </p>
   */
  public Optional<UserProfile> findById(UUID id) {
    return dsl.select(
            REFIQ_USER_PROFILES.ID,
            REFIQ_USER_PROFILES.NAME,
            REFIQ_USER_PROFILES.CONTACT_EMAIL,
            REFIQ_USER_PROFILES.CREATED_AT
        )
        .from(REFIQ_USER_PROFILES)
        .where(REFIQ_USER_PROFILES.ID.eq(id))
        .fetchOptional(r -> new UserProfile(
            r.get(REFIQ_USER_PROFILES.ID),
            r.get(REFIQ_USER_PROFILES.NAME),
            r.get(REFIQ_USER_PROFILES.CONTACT_EMAIL),
            r.get(REFIQ_USER_PROFILES.CREATED_AT).toInstant()
        ));
  }

  // HELPER METHODS

  /**
   * Clears the underlying table.
   * <p>
   * Exclusively intended for integration test teardown procedures.
   * </p>
   */
  public void deleteAll() {
    dsl.deleteFrom(com.refiq.platform.shared.db.generated.Tables.REFIQ_USER_PROFILES).execute();
  }
}