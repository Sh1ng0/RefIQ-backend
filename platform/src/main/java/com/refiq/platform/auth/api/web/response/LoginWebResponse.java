package com.refiq.platform.auth.api.web.response;



import com.refiq.platform.auth.api.dto.LoginResponse;
import com.refiq.platform.shared.web.ApiError;

/**
 * Contrato estricto de salida HTTP para el endpoint de login.
 * Adopta el patrón envoltorio (Envelope) para estandarizar las respuestas.
 */
public sealed interface LoginWebResponse {


  record Success(LoginResponse data) implements LoginWebResponse {}


  record Failure(ApiError error) implements LoginWebResponse {}

}