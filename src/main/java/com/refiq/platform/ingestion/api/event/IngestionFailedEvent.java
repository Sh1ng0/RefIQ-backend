package com.refiq.platform.ingestion.api.event;

import java.util.UUID;

/**
 * Represents a domain event published when the asynchronous ingestion process fails
 * (e.g., S3 connection timeout, multipart upload failure).
 */
public record IngestionFailedEvent(
    UUID fileId,
    String errorMessage
) {}