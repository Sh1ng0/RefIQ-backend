package com.refiq.platform.auth.internal.repository;

import com.refiq.platform.auth.internal.domain.Credential;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.time.ZoneOffset;
import java.util.Optional;

import static com.refiq.platform.shared.db.generated.Tables.REFIQ_CREDENTIALS;

/**
 * Data-oriented implementation of the credential repository.
 * <p>
 * Utilizes jOOQ to ensure type-safe queries and explicit state transitions,
 * exclusively handling and returning immutable domain records.
 * </p>
 */
@Repository
public class DbCredentialRepository {

  private final DSLContext dsl;

  public DbCredentialRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  /**
   * Verifies the existence of an email address using an optimized EXISTS query.
   */
  public boolean existsByEmail(String email) {
    return dsl.fetchExists(
        dsl.selectOne()
            .from(REFIQ_CREDENTIALS)
            .where(REFIQ_CREDENTIALS.EMAIL.eq(email))
    );
  }

  /**
   * Retrieves a pure credential record from the database.
   * <p>
   * Selects strictly necessary columns and delegates mapping to the canonical constructor
   * of the domain record.
   * </p>
   */
  public Optional<Credential> findByEmail(String email) {
    return dsl.select(
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
   * Inserts a new credential fact into the system.
   * <p>
   * Assumes the incoming record is already populated with its identifier and creation timestamp,
   * explicitly aligning the Java Instant with the database timestamp timezone.
   * </p>
   */
  public void insert(Credential credential) {
    dsl.insertInto(REFIQ_CREDENTIALS)
        .set(REFIQ_CREDENTIALS.ID, credential.id())
        .set(REFIQ_CREDENTIALS.EMAIL, credential.email())
        .set(REFIQ_CREDENTIALS.PASSWORD_HASH, credential.passwordHash())
        .set(REFIQ_CREDENTIALS.CREATED_AT, credential.createdAt().atOffset(ZoneOffset.UTC))
        .execute();
  }

  /**
   * Executes a precise state transition for an existing credential.
   * <p>
   * Explicitly alters only the password hash of the target credential identified by its ID.
   * </p>
   */
  public void updatePassword(Credential credential) {
    dsl.update(REFIQ_CREDENTIALS)
        .set(REFIQ_CREDENTIALS.PASSWORD_HASH, credential.passwordHash())
        .where(REFIQ_CREDENTIALS.ID.eq(credential.id()))
        .execute();
  }
}