package com.refiq.platform.calculation.api.web;

import com.refiq.platform.calculation.api.web.response.ResultWebResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.UUID;

@RequestMapping("/api/results")
@Tag(name = "Result Module", description = "Endpoints para consultar el estado y resultado del procesamiento asíncrono")
public interface ResultApi {

  @Operation(
      summary = "Consultar resultado del análisis",
      description = "Permite al cliente hacer polling mediante el Correlation ID (fileId) para obtener el estado del cálculo o el resultado final del motor estadístico."
  )
  @ApiResponses(value = {
      @ApiResponse(
          responseCode = "200",
          description = "Cálculo finalizado con éxito. Devuelve los intervalos de referencia.",
          content = @Content(
              mediaType = MediaType.APPLICATION_JSON_VALUE,
              schema = @Schema(implementation = ResultWebResponse.Success.class)
          )
      ),
      @ApiResponse(
          responseCode = "202",
          description = "Procesamiento en curso (PENDING). El cliente debe seguir haciendo polling.",
          content = @Content(
              mediaType = MediaType.APPLICATION_JSON_VALUE,
              schema = @Schema(implementation = ResultWebResponse.Success.class), // O un record específico "Pending"
              examples = @ExampleObject(
                  name = "ProcessingExample",
                  value = "{\n  \"data\": {\n    \"status\": \"PENDING\",\n    \"message\": \"El archivo se está procesando en el Data Lake o motor R\"\n  }\n}"
              )
          )
      ),
      @ApiResponse(
          responseCode = "404",
          description = "No se encontró el registro de seguimiento para el ID proporcionado.",
          content = @Content(
              mediaType = MediaType.APPLICATION_JSON_VALUE,
              schema = @Schema(implementation = ResultWebResponse.Failure.class),
              examples = @ExampleObject(
                  name = "NotFoundExample",
                  value = "{\n  \"error\": {\n    \"error\": \"No se encontraron resultados para el ID proporcionado.\",\n    \"details\": null\n  }\n}"
              )
          )
      ),
      @ApiResponse(
          responseCode = "422",
          description = "Fallo de negocio en el cálculo (Ej. CSV inválido, falta de datos).",
          content = @Content(
              mediaType = MediaType.APPLICATION_JSON_VALUE,
              schema = @Schema(implementation = ResultWebResponse.Failure.class),
              examples = @ExampleObject(
                  name = "DataInconsistencyExample",
                  value = "{\n  \"error\": {\n    \"error\": \"El CSV canónico no cumple el contrato: Falta columna 'value'.\",\n    \"details\": null\n  }\n}"
              )
          )
      ),
      @ApiResponse(
          responseCode = "500",
          description = "Fallo técnico irrecuperable (El proceso asíncrono falló y no reintentará más).",
          content = @Content(
              mediaType = MediaType.APPLICATION_JSON_VALUE,
              schema = @Schema(implementation = ResultWebResponse.Failure.class),
              examples = @ExampleObject(
                  name = "EngineFailedExample",
                  value = "{\n  \"error\": {\n    \"error\": \"Error interno del motor de análisis tras múltiples intentos.\",\n    \"details\": null\n  }\n}"
              )
          )
      )
  })
  @GetMapping(value = "/{fileId}", produces = MediaType.APPLICATION_JSON_VALUE)
  ResponseEntity<ResultWebResponse> getResult(
      @Parameter(description = "UUID del archivo proporcionado durante la ingesta", required = true)
      @PathVariable("fileId") UUID fileId
  );
}