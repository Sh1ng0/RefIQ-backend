package com.refiq.platform.ingestion.api.event;

import java.util.UUID;

/**
 * Domain event published when a new file is successfully accepted by the ingestion layer.
 * Notifies downstream modules (like Calculation) to initialize their tracking structures.
 */
public record FileIngestedEvent(
    UUID fileId,
    String testCode
) {}