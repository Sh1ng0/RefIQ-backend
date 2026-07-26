package com.refiq.platform.ingestion.api.web.response;



import com.refiq.platform.ingestion.api.dto.IngestionResponse;
import com.refiq.platform.shared.web.ApiError;

public sealed interface IngestionWebResponse {

  record Success(IngestionResponse data) implements IngestionWebResponse {}

  record Failure(ApiError error) implements IngestionWebResponse {}

}