package com.refiq.platform.auth.api.web.response;



import com.refiq.platform.auth.api.dto.RegistrationResponse;
import com.refiq.platform.shared.web.ApiError;

/**
 * Contrato estricto de salida HTTP para el endpoint de registro.
 * Adopta el patrón envoltorio (Envelope) para estandarizar las respuestas.
 */
public sealed interface RegistrationWebResponse {

  // Generará: { "data": { ... } }
  record Success(RegistrationResponse data) implements RegistrationWebResponse {}

  // Generará: { "error": { "error": "Mensaje...", "details": {...} } }
  record Failure(ApiError error) implements RegistrationWebResponse {}

}