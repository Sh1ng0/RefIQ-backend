package com.refiq.platform.calculation.internal.logging;


import org.slf4j.Logger;
import java.util.UUID;

/**
 * Domain-specific structured log events for the Calculation module.
 * Implemented as a sealed interface to guarantee exhaustive pattern matching.
 */
public sealed interface CalculationLogEvent {

  // --- Calculation Flow ---
  record CalculationStarted(String s3Key, double percentileLow, double percentileHigh) implements CalculationLogEvent {}
  record PlumberRequestSent(String s3Key) implements CalculationLogEvent {}
  record CalculationCompleted(String s3Key) implements CalculationLogEvent {}
  record PlumberError(String errorDetails) implements CalculationLogEvent {}
  record AnalysisInitiated(String s3Key) implements CalculationLogEvent {}

  // --- R Engine (Plumber) Interactions ---
  record RResponseReceived(String rawJson) implements CalculationLogEvent {}
  record RDeserializationError(String rawJson) implements CalculationLogEvent {}
  record RTechnicalError(String status) implements CalculationLogEvent {}

  // --- MinIO Webhooks ---
  record MinioWebhookReceived(String s3Key) implements CalculationLogEvent {}
  record MinioWebhookParsingWarn(String path) implements CalculationLogEvent {}
  record MinioWebhookError(String errorDetails) implements CalculationLogEvent {}
  record MinioWebhookDuplicateIgnored(String analyte, String dbState) implements CalculationLogEvent {}

  // --- Calculation Results Handling ---
  record CalculationSuccess(String test, String range) implements CalculationLogEvent {}
  record CalculationDataInconsistency(String test, String details) implements CalculationLogEvent {}
  record CalculationEngineUnavailable(String test, String details) implements CalculationLogEvent {}
  record CalculationInvalidRequest(String s3Key) implements CalculationLogEvent {}

  // --- Persistence ---
  record CalculationResultSaved(UUID fileId) implements CalculationLogEvent {}

  // --- Business Inconsistencies ---
  record DataInconsistencyTracked(UUID fileId, String details) implements CalculationLogEvent {}


  default void log(Logger logger) {
    switch (this) {
      case CalculationStarted e -> logger.atInfo()
          .setMessage("Initiating range calculation for file")
          .addKeyValue("s3_key", e.s3Key())
          .addKeyValue("percentile_low", e.percentileLow())
          .addKeyValue("percentile_high", e.percentileHigh())
          .log();

      case PlumberRequestSent e -> logger.atDebug()
          .setMessage("Sending request to Plumber")
          .addKeyValue("s3_key", e.s3Key())
          .log();

      case CalculationCompleted e -> logger.atInfo()
          .setMessage("Calculation successfully completed for test")
          .addKeyValue("s3_key", e.s3Key())
          .log();

      case PlumberError e -> logger.atError()
          .setMessage("Error in the R engine (Plumber)")
          .addKeyValue("error_details", e.errorDetails())
          .log();

      case AnalysisInitiated e -> logger.atDebug()
          .setMessage("Sending request to R Plumber")
          .addKeyValue("s3_key", e.s3Key())
          .log();

      case RResponseReceived e -> logger.atDebug()
          .setMessage("Raw JSON received from R")
          .addKeyValue("raw_json", e.rawJson())
          .log();

      case RDeserializationError e -> logger.atError()
          .setMessage("Critical error deserializing R response")
          .addKeyValue("raw_json", e.rawJson())
          .log();

      case RTechnicalError e -> logger.atError()
          .setMessage("The R engine returned an HTTP error")
          .addKeyValue("status", e.status())
          .log();

      case MinioWebhookReceived e -> logger.atInfo()
          .setMessage("MinIO webhook received for Gold file")
          .addKeyValue("s3_key", e.s3Key())
          .log();

      case MinioWebhookParsingWarn e -> logger.atWarn()
          .setMessage("Could not extract analyte code from path")
          .addKeyValue("path", e.path())
          .log();

      case MinioWebhookError e -> logger.atError()
          .setMessage("Unexpected error processing MinIO webhook")
          .addKeyValue("error_details", e.errorDetails())
          .log();

      case MinioWebhookDuplicateIgnored e -> logger.atInfo()
          .setMessage("Duplicate webhook ignored for analyte")
          .addKeyValue("analyte", e.analyte())
          .addKeyValue("db_state", e.dbState())
          .log();

      case CalculationSuccess e -> logger.atInfo()
          .setMessage("Successful calculation")
          .addKeyValue("test", e.test())
          .addKeyValue("range", e.range())
          .log();

      case CalculationDataInconsistency e -> logger.atWarn()
          .setMessage("Data inconsistency")
          .addKeyValue("test", e.test())
          .addKeyValue("details", e.details())
          .log();

      case CalculationEngineUnavailable e -> logger.atError()
          .setMessage("R engine unavailable")
          .addKeyValue("test", e.test())
          .addKeyValue("details", e.details())
          .log();

      case CalculationInvalidRequest e -> logger.atError()
          .setMessage("Invalid request generated by Webhook")
          .addKeyValue("s3_key", e.s3Key())
          .log();

      case CalculationResultSaved e -> logger.atInfo()
          .setMessage("Calculation successful. JSON saved in DB")
          .addKeyValue("file_id", e.fileId())
          .log();

      case DataInconsistencyTracked e -> logger.atWarn()
          .setMessage("Data inconsistency detected and tracked")
          .addKeyValue("file_id", e.fileId())
          .addKeyValue("details", e.details())
          .log();
    }
  }
}