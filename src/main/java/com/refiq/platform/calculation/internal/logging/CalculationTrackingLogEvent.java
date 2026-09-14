package com.refiq.platform.calculation.internal.logging;

import org.slf4j.Logger;
import java.util.UUID;

/**
 * Domain-specific structured log events for tracking calculations.
 * Maintained in the centralized internal logging package for module consistency.
 */
public sealed interface CalculationTrackingLogEvent {

  // --- Ingestion Success Tracking ---
  record TrackingEventReceived(UUID fileId) implements CalculationTrackingLogEvent {}
  record TrackingRecordCreated(UUID fileId) implements CalculationTrackingLogEvent {}
  record TrackingAlreadyHandled(UUID fileId, String status) implements CalculationTrackingLogEvent {}

  // --- Ingestion Failure Tracking ---
  record IngestionFailedEventReceived(UUID fileId, String reason) implements CalculationTrackingLogEvent {}
  record TrackingRecordUpdatedToFailed(UUID fileId) implements CalculationTrackingLogEvent {}

  // --- Common Tracking ---
  record TrackingRecordNotFound(UUID fileId) implements CalculationTrackingLogEvent {}

  default void log(Logger logger) {
    switch (this) {
      case TrackingEventReceived e -> logger.atDebug()
          .setMessage("Received new ingestion event (Modulith)")
          .addKeyValue("file_id", e.fileId())
          .log();

      case TrackingRecordCreated e -> logger.atInfo()
          .setMessage("PENDING tracking record created in DB")
          .addKeyValue("file_id", e.fileId())
          .log();

      case TrackingAlreadyHandled e -> logger.atInfo()
          .setMessage("Calculation not claimed. Already handled.")
          .addKeyValue("file_id", e.fileId())
          .addKeyValue("current_status", e.status())
          .log();

      case IngestionFailedEventReceived e -> logger.atWarn()
          .setMessage("Received ingestion failure event")
          .addKeyValue("file_id", e.fileId())
          .addKeyValue("reason", e.reason())
          .log();

      case TrackingRecordUpdatedToFailed e -> logger.atInfo()
          .setMessage("Tracking state updated to FAILED in DB after ingestion error")
          .addKeyValue("file_id", e.fileId())
          .log();

      case TrackingRecordNotFound e -> logger.atError()
          .setMessage("WARNING! Tracking entity not found")
          .addKeyValue("file_id", e.fileId())
          .log();
    }
  }
}