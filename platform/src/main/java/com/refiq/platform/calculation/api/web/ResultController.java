package com.refiq.platform.calculation.api.web;

import com.refiq.platform.calculation.api.web.response.ResultResponse;
import com.refiq.platform.calculation.api.web.response.ResultWebResponse;
import com.refiq.platform.calculation.internal.domain.CalculationState;
import com.refiq.platform.calculation.internal.repository.DbCalculationResultRepository;
import com.refiq.platform.shared.web.ApiError;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/results")
@RequiredArgsConstructor
public class ResultController implements ResultApi {

  // Inyectamos el nuevo repositorio jOOQ, perfecto para consultas de lectura directa
  private final DbCalculationResultRepository repository;

  @Override
  public ResponseEntity<ResultWebResponse> getResult(@PathVariable UUID fileId) {
    return repository.findById(fileId)
        .map(state -> {

          // 1. Pattern Matching limpio para crear el DTO de respuesta
          ResultWebResponse responseBody = switch (state) {
            case CalculationState.Pending p ->
                new ResultWebResponse.Success(new ResultResponse.Pending("Calculation is pending"));

            case CalculationState.Processing p ->
                new ResultWebResponse.Success(new ResultResponse.Processing("Calculation in progress"));

            case CalculationState.Failed f ->
                new ResultWebResponse.Failure(new ApiError("Error en el cálculo", f.errorMessage()));

            case CalculationState.Success s ->
                new ResultWebResponse.Success(new ResultResponse.Success(s.payload()));
          };

          // 2. Resolvemos el HttpStatus según el estado
          HttpStatus status = switch (state) {
            case CalculationState.Failed f -> HttpStatus.INTERNAL_SERVER_ERROR;
            case CalculationState.Success s -> HttpStatus.OK;
            default -> HttpStatus.ACCEPTED; // Pending & Processing
          };

          // 3. Devolvemos el ResponseEntity sin necesidad de casteos feos
          var responseEntityBuilder = ResponseEntity.status(status);

          if (state instanceof CalculationState.Success) {
            responseEntityBuilder.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
          }

          return responseEntityBuilder.body(responseBody);
        })
        .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(new ResultWebResponse.Failure(
                new ApiError("No se encontraron resultados para el ID proporcionado.")
            ))
        );
  }
}