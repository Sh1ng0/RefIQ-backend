package com.refiq.platform.ingestion.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Represents the sealed result of the file ingestion operation.
 * <p>
 * This interface models the outcome flow (success or specific failures) without relying on
 * control-flow exceptions, allowing the API adapter to map results directly to HTTP responses.
 * </p>
 */

// IMPORTANTE: Le decimos a Swagger cuáles son las implementaciones posibles
@Schema(oneOf = {
    IngestionResult.Success.class,
    IngestionResult.InvalidFile.class,
    IngestionResult.StorageUnavailable.class
})
public sealed interface IngestionResult {

  /**
   * The file was successfully accepted and queued for processing.
   */
  record Success(IngestionResponse response) implements IngestionResult {}

  /**
   * The file was rejected due to validation errors (e.g., empty file, bad request).
   * Prevents runtime exceptions like IllegalArgumentException in business logic.
   */
  record InvalidFile(String filename, String reason) implements IngestionResult {}

  /**
   * Technical failure preventing file storage (e.g., S3 outage, I/O error).
   * Allows the controller to decide whether to retry or return a 503 Service Unavailable.
   */
  record StorageUnavailable(String debugInfo) implements IngestionResult {}
}