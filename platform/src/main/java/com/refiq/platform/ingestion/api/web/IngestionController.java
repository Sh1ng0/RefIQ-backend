package com.refiq.platform.ingestion.api.web;

import com.refiq.platform.ingestion.api.dto.IngestionResponse;
import com.refiq.platform.ingestion.api.dto.IngestionResult;
import com.refiq.platform.ingestion.internal.domain.IngestionFile;
import com.refiq.platform.ingestion.internal.service.IngestionService;

import com.refiq.platform.shared.web.ApiError;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * REST Controller handling the reception and orchestration of ingestion files (CSVs).
 * <p>
 * Acting as the Primary Adapter, it transforms HTTP Multipart requests into domain-agnostic
 * objects. Its main responsibility is ensuring the file is physically available for asynchronous
 * processing before releasing the HTTP connection.
 * </p>
 */
@RestController
@RequestMapping("/api/ingestion")
@RequiredArgsConstructor
@Tag(name = "Ingestion Module", description = "Endpoints for CSV upload and normalization")
public class IngestionController {

  private static final Logger log = LoggerFactory.getLogger(IngestionController.class);

  private final IngestionService ingestionService;

  /**
   * Main endpoint for CSV file upload.
   * <p>
   * Implements a "Fire-and-Forget" pattern:
   * <ol>
   * <li>Validates that the received file is not empty.</li>
   * <li>Persists the file to a temporary disk location (to survive the request lifecycle).</li>
   * <li>Delegates processing to the domain service (asynchronous).</li>
   * <li>Returns a 202 ACCEPTED response immediately.</li>
   * </ol>
   * </p>
   *
   * @param file The CSV file received as `multipart/form-data`.
   * @return {@link ResponseEntity} containing the operation result:
   * <ul>
   * <li>202 ACCEPTED: File successfully received and queued.</li>
   * <li>400 BAD REQUEST: File is empty or I/O error during temporary storage.</li>
   * <li>503 SERVICE UNAVAILABLE: Critical failure in the storage system.</li>
   * </ul>
   */

  @Operation(
      summary = "Upload CSV file",
      description = "Uploads a CSV file for asynchronous processing. Returns a tracking ID immediately."
  )
  @ApiResponses(value = {
      @ApiResponse(
          responseCode = "202",
          description = "File accepted for processing",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(implementation = IngestionResponse.class)
          )
      ),
      @ApiResponse(
          responseCode = "400",
          description = "Invalid or empty file",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(implementation = ApiError.class)
          )
      ),
      @ApiResponse(
          responseCode = "503",
          description = "Storage system error",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(implementation = ApiError.class)
          )
      )
  })
  @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)

  public ResponseEntity<?> upload(
      @RequestParam("file")
      @Schema(type = "string", format = "binary", description = "Archivo CSV raw")
      MultipartFile file) {

    if (file.isEmpty()) {
      return ResponseEntity.badRequest().body(new ApiError("El archivo está vacío."));
    }

    try {

      IngestionFile domainFile = mapToSafeDomainFile(file);

      IngestionResult result = ingestionService.ingest(domainFile);

      return mapToResponse(result);

    } catch (IOException e) {
      log.error("Error I/O en la capa web al procesar archivo temporal", e);
      return ResponseEntity.badRequest()
          .body(new ApiError( "Error al procesar el archivo temporal."));
    }
  }

  // -------------------------------------------------------------------------
  // HELPER METHODS
  // -------------------------------------------------------------------------

  /**
   * Maps a Spring {@link MultipartFile} to a safe domain {@link IngestionFile}.
   * <p>
   * <strong>Why copy the file?</strong><br>
   * The {@code MultipartFile} input stream is tied to the HTTP request lifecycle. Since processing
   * occurs in a separate Virtual Thread after the response is sent, the content is copied to a
   * temporary physical file to avoid "Stream Closed" errors.
   * </p>
   * <p>
   * A cleanup callback is injected so the Service can delete this temporary file once processing
   * ends.
   * </p>
   *
   * @param file The original multipart file.
   * @return A domain-safe object referencing the temporary file and its cleanup logic.
   * @throws IOException If writing to the temporary disk location fails.
   */
  private IngestionFile mapToSafeDomainFile(MultipartFile file) throws IOException {

    Path tempPath = Files.createTempFile("refiq-ingest-", ".tmp");
    file.transferTo(tempPath);

    return new IngestionFile(
        file.getOriginalFilename(),
        // Supplier no soporta UNcheckedExceptions
        () -> {
          try {
            return new FileInputStream(tempPath.toFile());
          } catch (IOException e) {
            throw new java.io.UncheckedIOException("No se pudo abrir el archivo temporal", e);
          }
        },

        file.getSize(),
        file.getContentType(),

        // 3. Callback de limpieza (se mantiene igual)
        () -> {
          try {
            Files.deleteIfExists(tempPath);
            log.trace("Archivo temporal eliminado: {}", tempPath);
          } catch (IOException e) {
            log.warn("No se pudo borrar temporal: {}", tempPath);
          }
        }
    );
  }

  /**
   * Mapea el resultado sellado del dominio (Pattern Matching) a la respuesta HTTP adecuada.
   */
  private ResponseEntity<?> mapToResponse(IngestionResult result) {
    return switch (result) {
      case IngestionResult.Success s -> ResponseEntity.accepted().body(s.response());

      // Aquí estandarizamos: Convertimos el 'reason' del record a nuestro ApiError
      case IngestionResult.InvalidFile e -> ResponseEntity.badRequest()
          .body(new ApiError("Archivo inválido", Map.of("reason", e.reason())));

      case IngestionResult.StorageUnavailable e -> ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
          .body(new ApiError("Servicio no disponible", Map.of("debug", e.debugInfo())));
    };
  }
}