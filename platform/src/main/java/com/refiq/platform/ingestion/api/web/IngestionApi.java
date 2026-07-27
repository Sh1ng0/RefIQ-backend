package com.refiq.platform.ingestion.api.web;

import com.refiq.platform.ingestion.api.web.response.IngestionWebResponse;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

/**
 * Defines the contract for the Data Ingestion API.
 * <p>
 * This interface serves as the primary entry point for ingesting raw payloads into the system.
 * It separates the API definition and OpenAPI documentation from the underlying
 * controller implementation, keeping the business logic clean and focused.
 * </p>
 */
@RequestMapping("/api/ingestion")
@Tag(name = "Ingestion Module", description = "Endpoints for routing raw CSV datasets to the Data Lake")
public interface IngestionApi {

  @Operation(
      summary = "Upload raw CSV file to Data Lake",
      description = "Uploads a raw CSV file for asynchronous transfer to the Data Lake. " +
          "Files are routed to specific folders based on the provided Analyte. " +
          "Returns a tracking UUID immediately."
  )
  @ApiResponses(value = {
      @ApiResponse(
          responseCode = "202",
          description = "File accepted and transfer to Data Lake initiated",
          content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = IngestionWebResponse.Success.class))
      ),
      @ApiResponse(
          responseCode = "400",
          description = "Invalid request (e.g., missing file, empty file, or unsupported analyte)",
          content = @Content(
              mediaType = MediaType.APPLICATION_JSON_VALUE,
              schema = @Schema(implementation = IngestionWebResponse.Failure.class),
              examples = {
                  @ExampleObject(
                      name = "UnsupportedAnalyteExample",
                      value = "{\n  \"error\": {\n    \"error\": \"Analito no soportado: INVENTADO\",\n    \"details\": null\n  }\n}"
                  ),
                  @ExampleObject(
                      name = "EmptyFileExample",
                      value = "{\n  \"error\": {\n    \"error\": \"El archivo está vacío.\",\n    \"details\": null\n  }\n}"
                  )
              }
          )
      ),
      @ApiResponse(
          responseCode = "503",
          description = "Storage system error",
          content = @Content(
              mediaType = MediaType.APPLICATION_JSON_VALUE,
              schema = @Schema(implementation = IngestionWebResponse.Failure.class),
              examples = @ExampleObject(
                  name = "StorageUnavailableExample",
                  value = "{\n  \"error\": {\n    \"error\": \"Servicio no disponible\",\n    \"details\": {\n      \"debug\": \"S3 Timeout Connection Exception\"\n    }\n  }\n}"
              )
          )
      )
  })
  @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
  ResponseEntity<IngestionWebResponse> upload(
      @Parameter(description = "Archivo CSV raw", required = true)
      @RequestPart("file")
      MultipartFile file,

      @Parameter(description = "Código del analito clínico (ej. ALP, CRE). Debe coincidir con los valores permitidos en el sistema.", required = true)
      @RequestParam("analyte")
      String analyteStr);
}