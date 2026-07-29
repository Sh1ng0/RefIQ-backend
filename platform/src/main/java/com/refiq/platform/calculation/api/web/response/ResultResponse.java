package com.refiq.platform.calculation.api.web.response;


import com.fasterxml.jackson.annotation.JsonRawValue;




/**
 * DTO que representa los posibles estados de salida de una consulta de resultados.
 */
public sealed interface ResultResponse {

  record Pending(String message) implements ResultResponse {}

  record Processing(String message) implements ResultResponse {}

//  record Failing(String error) implements ResultResponse {}

  // @JsonRawValue evita que Jackson escape el String JSON que viene de la BD
  record Success(@JsonRawValue String payload) implements ResultResponse {}
}