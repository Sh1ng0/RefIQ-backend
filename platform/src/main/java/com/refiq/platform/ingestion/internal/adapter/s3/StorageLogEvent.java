package com.refiq.platform.ingestion.internal.adapter.s3;

import com.refiq.platform.shared.observability.Loggable;

/**
 * Defines logging events specific to the persistence layer (S3).
 * <p>
 * Decouples infrastructure details from the core domain.
 * </p>
 */
enum StorageLogEvent implements Loggable {

  SINGLE_UPLOAD_SUCCESS(LogLevel.DEBUG, "Direct upload to S3 completed. Bucket: {}, Key: {}"),

  MULTIPART_INITIATED(LogLevel.DEBUG, "S3 Multipart transaction initiated. UploadId: {}"),

//  PART_UPLOADED(LogLevel.TRACE, "Part #{} uploaded to S3. Key: {}, ETag: {}"),

  MULTIPART_COMPLETED(LogLevel.INFO, "Multipart upload successfully completed. Key: {}"),

  MULTIPART_ABORTED(LogLevel.WARN, "Multipart upload aborted (Best Effort). Key: {}, ID: {}"),

  ABORT_FAILED(LogLevel.ERROR, "Failed to abort the upload: {}");

  private final LogLevel level;
  private final String template;

  StorageLogEvent(LogLevel level, String template) {
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