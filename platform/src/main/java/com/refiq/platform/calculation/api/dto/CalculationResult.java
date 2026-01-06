package com.refiq.platform.calculation.api.dto;

/**
 * Representa el resultado de la operación de cálculo de rangos.
 * Sigue el patrón de tipos sellados para un manejo exhaustivo en el controlador.
 */
public sealed interface CalculationResult {

  /**
   * Cálculo finalizado con éxito. Contiene la respuesta lista para el Front-end.
   */
  record Success(CalculationResponse response) implements CalculationResult {}

  /**
   * Error en los parámetros enviados (ej. percentiles inválidos o test_code inexistente).
   */
  record InvalidRequest(String reason) implements CalculationResult {}

  /**
   * El motor de R (Plumber) no está disponible o ha devuelto un error técnico.
   */
  record EngineUnavailable(String debugInfo) implements CalculationResult {}

  /**
   * El archivo en S3 no existe o no tiene datos suficientes para que el algoritmo converja.
   */
  // Mirar como el servicio gestiona esto, quizá mirar de generar más entradas en el logger
    // SObre todo para qué tipos de inconstiencia pueden haber y qué enviarle al front de manera más clara
    // Se cayó la red? Archivo corrupto? Etc
  record DataInconsistency(String details) implements CalculationResult {}
}