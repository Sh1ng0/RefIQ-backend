package com.refiq.platform.ingestion.internal.service;

import com.refiq.platform.shared.observability.Loggable;

/**
 * Defines the event log catalog for the ingestion service (Data Lake routing).
 */
enum IngestionLogEvent implements Loggable {

  // --- Phase 1: Reception ---
  UPLOAD_INITIATED(LogLevel.INFO, "Initiating Data Lake ingestion. File: '{}', Analyte: '{}', Size: {} bytes"),

  // --- Phase 2: Streaming to S3 (Bronze) ---
  BRONZE_PROCESSING_STARTED(LogLevel.DEBUG, "Starting streaming to Bronze layer. S3 Destination: {}"),
  MULTIPART_INITIATED(LogLevel.DEBUG, "S3 Multipart transaction initiated. UploadId: {}"),
  MULTIPART_PART_UPLOADED(LogLevel.DEBUG, "Part #{} successfully uploaded. ETag: {}"),
  MULTIPART_PART_FAILED(LogLevel.ERROR, "Failed to upload chunk #{}. Error: {}"),

  // --- Results ---
  BRONZE_SUMMARY(LogLevel.INFO, "Bronze ingestion successfully completed [FileID: {}]. Processed chunks: {}"),

  // --- Global Errors ---
  PIPELINE_ERROR(LogLevel.ERROR, "Critical failure in ingestion pipeline [FileID: {}]. Cause: {}"),
  MULTIPART_ABORTED(LogLevel.ERROR, "Multipart transaction aborted due to critical error. Key: {}"),

  // --- Data Lake Trigger ---
  DATALAKE_TRIGGER_SENT(LogLevel.INFO, "Data Lake successfully notified via HTTP to start pipeline."),
  DATALAKE_TRIGGER_FAILED(LogLevel.WARN, "Failed to notify Data Lake. File is in S3, but pipeline did not start: {}");

  private final LogLevel level;
  private final String template;

  IngestionLogEvent(LogLevel level, String template) {
    this.level = level;
    this.template = template;
  }

  @Override
  public LogLevel getLevel() {
    return level;
  }

  @Override
  public String getMessageTemplate() {
    return template;
  }
}