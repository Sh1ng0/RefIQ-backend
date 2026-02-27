package com.refiq.platform.ingestion.api.web;

import com.refiq.platform.ingestion.api.dto.IngestionResponse;
import com.refiq.platform.shared.web.ApiError;
import io.swagger.v3.oas.annotations.Operation;
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
import org.springframework.web.multipart.MultipartFile;


/**
 * Defines the contract for the Data Ingestion API.
 * <p>
 * This interface serves as the primary entry point for ingesting payloads into the system.
 * It separates the API definition and OpenAPI documentation from the underlying
 * controller implementation, keeping the business logic clean and focused.
 * </p>
 */
@RequestMapping("/api/ingestion")
@Tag(name = "Ingestion Module", description = "Endpoints for CSV upload and normalization")
public interface IngestionApi {

  @Operation(
      summary = "Upload CSV file",
      description = "Uploads a CSV file for asynchronous processing. Returns a tracking ID immediately."
  )
  @ApiResponses(value = {
      @ApiResponse(
          responseCode = "202",
          description = "File accepted for processing",
          content = @Content(mediaType = "application/json", schema = @Schema(implementation = IngestionResponse.class))
      ),
      @ApiResponse(
          responseCode = "400",
          description = "Invalid or empty file",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(implementation = ApiError.class),
              // AQUÍ ESTÁ LA MAGIA PARA EL 400
              examples = @ExampleObject(
                  name = "InvalidFileExample",
                  value = "{\n  \"error\": \"Archivo inválido\",\n  \"details\": {\n    \"reason\": \"El CSV no tiene la cabecera 'email' obligatoria\"\n  }\n}"
              )
          )
      ),
      @ApiResponse(
          responseCode = "503",
          description = "Storage system error",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(implementation = ApiError.class),

              examples = @ExampleObject(
                  name = "StorageUnavailableExample",
                  value = "{\n  \"error\": \"Servicio no disponible\",\n  \"details\": {\n    \"debug\": \"S3 Timeout Connection Exception\"\n  }\n}"
              )
          )
      )
  })
  @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  ResponseEntity<?> upload(
      @RequestParam("file")
      @Schema(type = "string", format = "binary", description = "Archivo CSV raw")
      MultipartFile file);
}