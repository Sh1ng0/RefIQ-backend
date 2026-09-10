package com.refiq.platform.calculation.api.web.response;

import com.fasterxml.jackson.annotation.JsonRawValue;

/**
 * Represents the possible output states for a result query.
 */
public sealed interface ResultResponse {

  record Pending(String message) implements ResultResponse {}

  record Processing(String message) implements ResultResponse {}

  // @JsonRawValue prevents Jackson from escaping the JSON String retrieved from the DB
  record Success(@JsonRawValue String payload) implements ResultResponse {}
}