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
   * Attempts to atomically insert a new credential fact into the system.
   * <p>
   * To prevent check-then-act race conditions, this operation delegates the uniqueness
   * constraint validation directly to the database engine. It assumes the incoming record
   * is already fully populated with its identifier and creation timestamp, explicitly
   * aligning the Java {@link java.time.Instant} with the database timestamp timezone.
   * </p>
   *
   * @param credential the immutable credential fact to be persisted.
   * @return {@code true} if the credential was successfully inserted, or {@code false} if
   *         a collision occurred (e.g., the email already exists).
   */
  public boolean tryInsert(Credential credential) {
    int affectedRows = dsl.insertInto(REFIQ_CREDENTIALS)
        .set(REFIQ_CREDENTIALS.ID, credential.id())
        .set(REFIQ_CREDENTIALS.EMAIL, credential.email())
        .set(REFIQ_CREDENTIALS.PASSWORD_HASH, credential.passwordHash())
        .set(REFIQ_CREDENTIALS.CREATED_AT, credential.createdAt().atOffset(ZoneOffset.UTC))
        .onConflict(REFIQ_CREDENTIALS.EMAIL) // Requiere que la columna email tenga restricción UNIQUE
        .doNothing()
        .execute();

    return affectedRows == 1;
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