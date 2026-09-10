package com.refiq.platform.shared.web;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

/**
 * Represents a standard API error response payload.
 * <p>
 * Encapsulates the details of an error that occurred during the API execution,
 * providing a consistent structure for clients to parse and handle exceptions.
 * </p>
 */
@Schema(description = "Standard error structure for RefIQ")
public record ApiError(
    @Schema(description = "Error code or short description")
    String error,

    @Schema(description = "Additional details (optional)")
    Map<String, String> details
) {

  /**
   * Constructs a simple error response without additional details.
   */
  public ApiError(String error) {
    this(error, (Map<String, String>) null);
  }

  /**
   * Constructs an error response with a single specific reason.
   * <p>
   * Safely handles null values to prevent map instantiation failures.
   * </p>
   */
  public ApiError(String error, String reason) {
    this(
        error,
        reason == null ? null : Map.of("reason", reason)
    );
  }
}