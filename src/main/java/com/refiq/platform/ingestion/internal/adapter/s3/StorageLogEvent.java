package com.refiq.platform.ingestion.internal.adapter.s3;

import org.slf4j.Logger;

/**
 * Defines logging events specific to the persistence layer (S3).
 * <p>
 * Decouples infrastructure details from the core domain.
 * Package-private visibility ensures these infrastructure logs cannot leak
 * into the core services.
 * </p>
 */
sealed interface StorageLogEvent {

  record SingleUploadSuccess(String bucket, String key) implements StorageLogEvent {}
  record MultipartInitiated(String uploadId) implements StorageLogEvent {}
  record MultipartCompleted(String key) implements StorageLogEvent {}
  record MultipartAborted(String key, String uploadId) implements StorageLogEvent {}
  record AbortFailed(String errorDetails) implements StorageLogEvent {}

  default void log(Logger logger) {
    switch (this) {
      case SingleUploadSuccess e -> logger.atDebug()
          .setMessage("Direct upload to S3 completed")
          .addKeyValue("bucket", e.bucket())
          .addKeyValue("key", e.key())
          .log();

      case MultipartInitiated e -> logger.atDebug()
          .setMessage("S3 Multipart transaction initiated")
          .addKeyValue("upload_id", e.uploadId())
          .log();

      case MultipartCompleted e -> logger.atInfo()
          .setMessage("Multipart upload successfully completed")
          .addKeyValue("key", e.key())
          .log();

      case MultipartAborted e -> logger.atWarn()
          .setMessage("Multipart upload aborted (Best Effort)")
          .addKeyValue("key", e.key())
          .addKeyValue("upload_id", e.uploadId())
          .log();

      case AbortFailed e -> logger.atError()
          .setMessage("Failed to abort the upload")
          .addKeyValue("error_details", e.errorDetails())
          .log();
    }
  }
}