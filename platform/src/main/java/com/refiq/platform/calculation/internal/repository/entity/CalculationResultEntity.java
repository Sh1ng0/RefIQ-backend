package com.refiq.platform.calculation.internal.repository.entity;


import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

// TODO refactorizar esto para que sea DOP ready (jOOQ / Spring JDBC)

/**
 * JPA Entity representing the persistence state of an asynchronous reference interval calculation.
 * <p>
 * This entity acts as a tracking mechanism (mailbox) for the End-to-End processing pipeline.
 * It is initially created in a {@code PENDING} state when a file is ingested, and it is subsequently
 * updated to either {@code SUCCESS} (storing the final JSON payload) or {@code FAILED} (storing the error reason)
 * once the statistical engine completes its job.
 * </p>
 */
@Entity
@Table(name = "calculation_results")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CalculationResultEntity {

  /**
   * The unique identifier for this calculation result.
   * <p>
   * This ID strictly corresponds to the original {@code fileId} generated during the Data Ingestion phase,
   * serving as the primary Correlation ID across the entire distributed architecture.
   * </p>
   */
  @Id
  @Column(name = "id", updatable = false, nullable = false)
  private UUID id;

  /**
   * The current lifecycle status of the calculation process.
   */
  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private CalculationStatus status;

  /**
   * The successful calculation output serialized as a JSON string.
   * <p>
   * Persisted natively as a {@code JSONB} column in PostgreSQL. This allows for high-performance
   * retrieval and opens the door for future deep JSON querying (e.g., filtering calculations by
   * specific analyte values) directly at the database level.
   * </p>
   */
  @JdbcTypeCode(SqlTypes.JSON) // <--- ¡LA MAGIA DE HIBERNATE 6!
  @Column(name = "payload", columnDefinition = "JSONB")
  private String payload;

  /**
   * A descriptive message detailing the cause of failure.
   * <p>
   * Populated only when the {@code status} transitions to {@code FAILED}. This can contain business
   * logic inconsistencies (e.g., insufficient data points) or technical errors (e.g., engine timeouts).
   * </p>
   */
  @Column(name = "error_message", columnDefinition = "TEXT")
  private String errorMessage;
}