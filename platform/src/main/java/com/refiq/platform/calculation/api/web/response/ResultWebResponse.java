package com.refiq.platform.calculation.api.web.response;



import com.refiq.platform.shared.web.ApiError;

public sealed interface ResultWebResponse {

  // Envuelve tu ResultResponse (Pending, Processing, Success) dentro de "data"
  record Success(ResultResponse data) implements ResultWebResponse {}

  // Envuelve los fallos y 404 dentro de "error"
  record Failure(ApiError error) implements ResultWebResponse {}
}