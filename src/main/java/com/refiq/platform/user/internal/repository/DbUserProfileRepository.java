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
   * Inserts a new user profile into the system.
   * <p>
   * Assumes the profile record is fully initialized with its identifier and creation timestamp,
   * explicitly aligning the Java {@link java.time.Instant} with the database {@code TIMESTAMP WITH TIME ZONE}.
   * </p>
   */
  public void insert(UserProfile profile) {
    dsl.insertInto(REFIQ_USER_PROFILES)
        .set(REFIQ_USER_PROFILES.ID, profile.id())
        .set(REFIQ_USER_PROFILES.NAME, profile.name())
        .set(REFIQ_USER_PROFILES.CONTACT_EMAIL, profile.contactEmail())
        .set(REFIQ_USER_PROFILES.CREATED_AT, profile.createdAt().atOffset(ZoneOffset.UTC))
        .execute();
  }

  /**
   * Retrieves a user profile by its unique identifier.
   * <p>
   * Delegates to jOOQ for mapping selected columns directly into the domain record's constructor.
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
        .fetchOptionalInto(UserProfile.class);
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