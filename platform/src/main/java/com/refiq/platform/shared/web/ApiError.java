package com.refiq.platform.shared.web;


import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;


/**
 * Represents a standard API error response payload.
 * <p>
 * This class encapsulates the details of an error that occurred during the API execution,
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
  public ApiError(String error) {
    this(error, null);
  }
}