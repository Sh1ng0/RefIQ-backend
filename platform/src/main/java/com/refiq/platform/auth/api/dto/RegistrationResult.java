package com.refiq.platform.auth.api.dto;


/**
 * Representa el resultado exhaustivo (sealed) de una operación de registro.
 * <p>
 * Esta interfaz sellada permite manejar el flujo de éxito o fallo de negocio mediante coincidencia
 * de patrones (Pattern Matching), eliminando la necesidad de excepciones para casos esperados como
 * duplicidad de emails.
 */
public sealed interface RegistrationResult {


  /**
   * Representa un registro exitoso.
   *
   * @param response Los datos de confirmación para el cliente.
   */
  record Success(RegistrationResponse response) implements RegistrationResult {

  }

  /**
   * Representa un fallo debido a que el email ya existe en el sistema.
   *
   * @param email El email que causó el conflicto.
   */
  record EmailAlreadyExists(String email) implements RegistrationResult {

  }
}