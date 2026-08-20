package com.refiq.platform.calculation.internal.repository;

import static com.refiq.platform.shared.db.generated.Tables.CALCULATION_RESULTS;

import com.refiq.platform.calculation.internal.domain.CalculationState;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.springframework.stereotype.Repository;

/**
 * Data-Oriented repository for the calculation tracking state machine.
 * Interacts directly with Postgres using jOOQ and maps rows to the Sealed Interface.
 */
@Repository
public class DbCalculationResultRepository {

  private final DSLContext dsl;

  public DbCalculationResultRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  /**
   * Inicializa el tracking insertando el estado PENDING.
   */
  public void insert(CalculationState.Pending state) {
    dsl.insertInto(CALCULATION_RESULTS)
        .set(CALCULATION_RESULTS.ID, state.id())
        .set(CALCULATION_RESULTS.STATUS, "PENDING")
        .execute();
  }

  /**
   * Cerrojo de concurrencia optimista (Idempotencia).
   * Intenta reclamar un cálculo pendiente para procesarlo.
   *
   * @return El número de filas afectadas (1 si lo reclamó con éxito, 0 si ya estaba reclamado).
   */
  public int claimPendingCalculation(UUID id) {
    return dsl.update(CALCULATION_RESULTS)
        .set(CALCULATION_RESULTS.STATUS, "PROCESSING")
        .where(CALCULATION_RESULTS.ID.eq(id))
        .and(CALCULATION_RESULTS.STATUS.eq("PENDING"))
        .execute();
  }

  /**
   * Recupera la fila de base de datos y la "rehidrata" instanciando EXCLUSIVAMENTE
   * el record de la interfaz sellada que corresponde a su estado actual.
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
            default -> throw new IllegalStateException("Estado desconocido en BD: " + status);
          };
        });
  }

  /**
   * Actualiza la fila en base de datos.
   * Utiliza Pattern Matching para generar la query SQL exacta según el tipo de estado,
   * sin setear campos irrelevantes a NULL manualmente.
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