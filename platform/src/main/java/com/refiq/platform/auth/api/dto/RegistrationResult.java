package com.refiq.platform.auth.api.dto;


/**
 * Representa el resultado exhaustivo (sealed) de una operación de registro.
 * <p>
 * Esta interfaz sellada permite manejar el flujo de éxito o fallo de negocio mediante coincidencia
 * de patrones (Pattern Matching), eliminando la necesidad de excepciones para casos esperados como
 * duplicidad de emails.
 */
public sealed interface RegistrationResult {

  record Success(RegistrationResponse response) implements RegistrationResult {

  }

  record EmailAlreadyExists(String email) implements RegistrationResult {

  }
}