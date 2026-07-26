package com.refiq.platform.calculation.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Represents the sealed result of the reference range calculation operation.
 * <p>
 * Follows the sealed interface pattern to enforce exhaustive handling of all possible outcomes
 * (Success, Invalid Request, Engine Failure, Data Inconsistency) in the controller layer.
 * </p>
 */

@Schema(oneOf = {
    CalculationResult.Success.class,
    CalculationResult.InvalidRequest.class,
    CalculationResult.EngineUnavailable.class,
    CalculationResult.DataInconsistency.class
})
public sealed interface CalculationResult {

  /**
   * The calculation completed successfully. Contains the response payload for the frontend.
   */
  record Success(CalculationResponse response) implements CalculationResult {}

  /**
   * The request parameters were invalid (e.g., impossible percentiles, missing keys).
   */
  record InvalidRequest(String reason) implements CalculationResult {}

  /**
   * Technical failure: The R engine (Plumber) is unavailable, timed out, or returned a 500 error.
   */
  record EngineUnavailable(String debugInfo) implements CalculationResult {}

  /**
   * Business failure: The input data is syntactically correct but statistically insufficient
   * (e.g., too few data points for convergence, non-normal distribution where required).
   * <p>
   * Maps to HTTP 422 Unprocessable Entity.
   * </p>
   */
  // Mirar como el servicio gestiona esto, quizá mirar de generar más entradas en el logger
  // SObre todo para qué tipos de inconstiencia pueden haber y qué enviarle al front de manera más clara
  // Se cayó la red? Archivo corrupto? Etc
  record DataInconsistency(String details) implements CalculationResult {}



  record AlreadyHandled(String status) implements CalculationResult {}
}