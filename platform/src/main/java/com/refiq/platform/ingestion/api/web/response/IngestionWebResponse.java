package com.refiq.platform.ingestion.api.web.response;

import com.refiq.platform.ingestion.api.dto.IngestionResponse;
import com.refiq.platform.shared.web.ApiError;

/**
 * Defines the strict HTTP output contract for the ingestion endpoint.
 * <p>
 * Adopts the envelope pattern to standardize API responses.
 * </p>
 */
public sealed interface IngestionWebResponse {

  record Success(IngestionResponse data) implements IngestionWebResponse {}

  record Failure(ApiError error) implements IngestionWebResponse {}
}