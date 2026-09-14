package com.refiq.platform.ingestion.internal.logging;

import org.slf4j.Logger;
import java.util.UUID;

/**
 * Domain-specific structured log events for the Ingestion module.
 * Implemented as a sealed interface to guarantee exhaustive pattern matching.
 */
public sealed interface IngestionLogEvent {

  // --- Phase 1: Reception ---
  record UploadInitiated(String filename, String analyte, long sizeBytes) implements IngestionLogEvent {}

  // --- Phase 2: Streaming to S3 (Bronze) ---
  record BronzeProcessingStarted(String s3Destination) implements IngestionLogEvent {}
  record MultipartInitiated(String uploadId) implements IngestionLogEvent {}
  record MultipartPartUploaded(int partNumber, String eTag) implements IngestionLogEvent {}
  record MultipartPartFailed(int partNumber, String errorDetails) implements IngestionLogEvent {}

  // --- Results ---
  record BronzeSummary(UUID fileId, int processedChunks) implements IngestionLogEvent {}

  // --- Global Errors ---
  record PipelineError(UUID fileId, String cause) implements IngestionLogEvent {}
  record MultipartAborted(String s3Key) implements IngestionLogEvent {}

  // --- Data Lake Trigger ---
  record DataLakeTriggerSent() implements IngestionLogEvent {}
  record DataLakeTriggerFailed(String errorDetails) implements IngestionLogEvent {}

  default void log(Logger logger) {
    switch (this) {
      case UploadInitiated e -> logger.atInfo()
          .setMessage("Initiating Data Lake ingestion")
          .addKeyValue("filename", e.filename())
          .addKeyValue("analyte", e.analyte())
          .addKeyValue("size_bytes", e.sizeBytes())
          .log();

      case BronzeProcessingStarted e -> logger.atDebug()
          .setMessage("Starting streaming to Bronze layer")
          .addKeyValue("s3_destination", e.s3Destination())
          .log();

      case MultipartInitiated e -> logger.atDebug()
          .setMessage("S3 Multipart transaction initiated")
          .addKeyValue("upload_id", e.uploadId())
          .log();

      case MultipartPartUploaded e -> logger.atDebug()
          .setMessage("Part successfully uploaded")
          .addKeyValue("part_number", e.partNumber())
          .addKeyValue("etag", e.eTag())
          .log();

      case MultipartPartFailed e -> logger.atError()
          .setMessage("Failed to upload chunk")
          .addKeyValue("part_number", e.partNumber())
          .addKeyValue("error_details", e.errorDetails())
          .log();

      case BronzeSummary e -> logger.atInfo()
          .setMessage("Bronze ingestion successfully completed")
          .addKeyValue("file_id", e.fileId())
          .addKeyValue("processed_chunks", e.processedChunks())
          .log();

      case PipelineError e -> logger.atError()
          .setMessage("Critical failure in ingestion pipeline")
          .addKeyValue("file_id", e.fileId())
          .addKeyValue("cause", e.cause())
          .log();

      case MultipartAborted e -> logger.atError()
          .setMessage("Multipart transaction aborted due to critical error")
          .addKeyValue("s3_key", e.s3Key())
          .log();

      case DataLakeTriggerSent e -> logger.atInfo()
          .setMessage("Data Lake successfully notified via HTTP to start pipeline")
          .log();

      case DataLakeTriggerFailed e -> logger.atWarn()
          .setMessage("Failed to notify Data Lake. File is in S3, but pipeline did not start")
          .addKeyValue("error_details", e.errorDetails())
          .log();
    }
  }
}