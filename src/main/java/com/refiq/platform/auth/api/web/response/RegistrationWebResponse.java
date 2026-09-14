package com.refiq.platform.auth.api.web.response;

import com.refiq.platform.auth.api.dto.registration.RegistrationResponse;
import com.refiq.platform.shared.web.ApiError;

/**
 * Defines the strict HTTP output contract for the registration endpoint.
 * <p>
 * Adopts the envelope pattern to standardize API responses.
 * </p>
 */
public sealed interface RegistrationWebResponse {

  // Generates: { "data": { ... } }
  record Success(RegistrationResponse data) implements RegistrationWebResponse {}

  // Generates: { "error": { "message": "...", "details": {...} } }
  record Failure(ApiError error) implements RegistrationWebResponse {}
}