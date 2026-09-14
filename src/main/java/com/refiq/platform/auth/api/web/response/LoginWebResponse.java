package com.refiq.platform.auth.api.web.response;

import com.refiq.platform.auth.api.dto.login.LoginResponse;
import com.refiq.platform.shared.web.ApiError;

/**
 * Defines the strict HTTP output contract for the login endpoint.
 * <p>
 * Adopts the envelope pattern to standardize API responses.
 * </p>
 */
public sealed interface LoginWebResponse {

  record Success(LoginResponse data) implements LoginWebResponse {}

  record Failure(ApiError error) implements LoginWebResponse {}
}