package com.refiq.platform.shared.web;


import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

@Schema(description = "Standard error structure for RefIQ")
public record ApiError(
    @Schema(description = "Error code or short description", example = "Validation Failed")
    String error,

    @Schema(description = "Additional details (optional)", example = "{\"email\": \"Invalid format\"}")
    Map<String, String> details
) {
  // Convenience constructor for simple errors
  public ApiError(String error) {
    this(error, null);
  }
}