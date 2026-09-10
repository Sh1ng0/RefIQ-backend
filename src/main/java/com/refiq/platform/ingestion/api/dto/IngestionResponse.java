package com.refiq.platform.ingestion.api.dto;


import java.util.UUID;

/**
 * Public confirmation data returned after a successful ingestion request.
 *
 * @param fileId The unique identifier (UUID) assigned to the file for tracking.
 * @param status The current status of the file (e.g., "UPLOADED", "PENDING_PROCESSING").
 */
public record IngestionResponse(UUID fileId, Status status) {

}
