package com.refiq.platform.calculation.internal.repository;

import static com.refiq.platform.shared.db.generated.Tables.CALCULATION_RESULTS;

import com.refiq.platform.calculation.internal.domain.CalculationState;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.springframework.stereotype.Repository;

/**
 * Data-oriented repository for the calculation tracking state machine.
 * <p>
 * Interacts directly with PostgreSQL using jOOQ and maps rows to the sealed domain interface.
 * </p>
 */
@Repository
public class DbCalculationResultRepository {

  private final DSLContext dsl;

  public DbCalculationResultRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  /**
   * Initializes the tracking flow by inserting the PENDING state.
   */
  public void insert(CalculationState.Pending state) {
    dsl.insertInto(CALCULATION_RESULTS)
        .set(CALCULATION_RESULTS.ID, state.id())
        .set(CALCULATION_RESULTS.STATUS, "PENDING")
        .execute();
  }

  /**
   * Optimistic concurrency lock for idempotency.
   * <p>
   * Attempts to claim a pending calculation to safely process it in a distributed environment.
   * </p>
   *
   * @return The number of affected rows (1 if successfully claimed, 0 if already processing/handled).
   */
  public int claimPendingCalculation(UUID id) {
    return dsl.update(CALCULATION_RESULTS)
        .set(CALCULATION_RESULTS.STATUS, "PROCESSING")
        .where(CALCULATION_RESULTS.ID.eq(id))
        .and(CALCULATION_RESULTS.STATUS.eq("PENDING"))
        .execute();
  }

  /**
   * Retrieves the database row and explicitly rehydrates it into the exact
   * sealed interface record that corresponds to its current state.
   */
  public Optional<CalculationState> findById(UUID id) {
    return dsl.selectFrom(CALCULATION_RESULTS)
        .where(CALCULATION_RESULTS.ID.eq(id))
        .fetchOptional(record -> {
          String status = record.getStatus();

          return switch (status) {
            case "PENDING" -> new CalculationState.Pending(record.getId());
            case "PROCESSING" -> new CalculationState.Processing(record.getId());
            case "SUCCESS" -> new CalculationState.Success(
                record.getId(),
                record.getPayload() != null ? record.getPayload().data() : null
            );
            case "FAILED" -> new CalculationState.Failed(record.getId(), record.getErrorMessage());
            default -> throw new IllegalStateException("Unknown database status: " + status);
          };
        });
  }

  /**
   * Updates the database row.
   * <p>
   * Leverages pattern matching to generate the exact SQL query depending on the state type,
   * avoiding the need to manually set irrelevant fields to NULL.
   * </p>
   */
  public void update(CalculationState state) {
    var step = dsl.update(CALCULATION_RESULTS);

    switch (state) {
      case CalculationState.Success s ->
          step.set(CALCULATION_RESULTS.STATUS, "SUCCESS")
              // Java String to postgres JSONB
              .set(CALCULATION_RESULTS.PAYLOAD, JSONB.valueOf(s.payload()))
              .where(CALCULATION_RESULTS.ID.eq(s.id()))
              .execute();

      case CalculationState.Failed f ->
          step.set(CALCULATION_RESULTS.STATUS, "FAILED")
              .set(CALCULATION_RESULTS.ERROR_MESSAGE, f.errorMessage())
              .where(CALCULATION_RESULTS.ID.eq(f.id()))
              .execute();

      case CalculationState.Processing p ->
          step.set(CALCULATION_RESULTS.STATUS, "PROCESSING")
              .where(CALCULATION_RESULTS.ID.eq(p.id()))
              .execute();

      case CalculationState.Pending p ->
          step.set(CALCULATION_RESULTS.STATUS, "PENDING")
              .where(CALCULATION_RESULTS.ID.eq(p.id()))
              .execute();
    }
  }
}