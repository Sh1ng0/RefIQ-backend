package com.refiq.platform.ingestion.api.event;

import java.util.UUID;

/**
 * Represents a domain event published when a new file is successfully accepted by the ingestion layer.
 * <p>
 * Notifies downstream modules (like Calculation) to initialize their tracking structures.
 * </p>
 */
public record FileAcceptedEvent(
    UUID fileId,
    String testCode
) {}