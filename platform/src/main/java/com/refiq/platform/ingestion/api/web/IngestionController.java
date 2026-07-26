package com.refiq.platform.ingestion.api.web;

import com.refiq.platform.ingestion.api.dto.IngestionResult;
import com.refiq.platform.ingestion.api.web.response.IngestionWebResponse;
import com.refiq.platform.ingestion.internal.domain.Analyte;
import com.refiq.platform.ingestion.internal.domain.IngestionFile;
import com.refiq.platform.ingestion.internal.service.IngestionService;
import com.refiq.platform.shared.web.ApiError;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * REST Controller handling the reception and orchestration of raw CSV ingestion files.
 * <p>
 * Acting as the Primary Adapter, it transforms HTTP Multipart requests into domain-agnostic
 * objects. Its main responsibilities are:
 * <ul>
 * <li>Validating the incoming clinical analyte against the supported {@link Analyte} enum.</li>
 * <li>Ensuring the file is physically available on disk for asynchronous processing.</li>
 * <li>Releasing the HTTP connection immediately with an accepted status.</li>
 * </ul>
 * </p>
 */
@RestController
@RequiredArgsConstructor
public class IngestionController implements IngestionApi {

  private static final Logger log = LoggerFactory.getLogger(IngestionController.class);

  private final IngestionService ingestionService;


  @Override
  public ResponseEntity<IngestionWebResponse> upload(MultipartFile file, String analyteStr) {

    // 1. Validación de archivo (Flujo de datos)
    if (file.isEmpty()) {
      return ResponseEntity.badRequest()
          .body(new IngestionWebResponse.Failure(new ApiError("El archivo está vacío.")));
    }

    // 2. Validación de Dominio (Sin excepciones, puro DOP)
    var analyteOpt = Analyte.fromString(analyteStr);
    if (analyteOpt.isEmpty()) {
      return ResponseEntity.badRequest()
          .body(new IngestionWebResponse.Failure(new ApiError("Analito no soportado: " + analyteStr)));
    }

    // 3. Infraestructura (Si esto lanza IOException, es excepcional y va al GlobalExceptionHandler)
    IngestionFile domainFile = mapToSafeDomainFile(file, analyteOpt.get());

    // 4. Ejecución del caso de uso
    IngestionResult result = ingestionService.ingest(domainFile);

    return mapToResponse(result);
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
   * A cleanup callback is injected so the Service can delete this temporary file once the
   * transfer to the Data Lake ends.
   * </p>
   *
   * @param file    The original multipart file.
   * @param analyte The strongly typed clinical analyte validated from the client request.
   * @return A domain-safe object referencing the temporary file and its cleanup logic.
   * @throws IOException If writing to the temporary disk location fails.
   */
  private IngestionFile mapToSafeDomainFile(MultipartFile file, Analyte analyte) {
    try {
      Path tempPath = Files.createTempFile("refiq-ingest-", ".tmp");
      file.transferTo(tempPath);

      return new IngestionFile(
          file.getOriginalFilename(),
          analyte,
          () -> {
            try {
              return new FileInputStream(tempPath.toFile());
            } catch (IOException e) {
              // Ya hacías esto aquí, ¡buen instinto!
              throw new java.io.UncheckedIOException("No se pudo abrir el archivo temporal", e);
            }
          },
          file.getSize(),
          file.getContentType(),
          () -> {
            try {
              Files.deleteIfExists(tempPath);
              log.trace("Archivo temporal eliminado: {}", tempPath);
            } catch (IOException e) {
              log.warn("No se pudo borrar temporal: {}", tempPath);
            }
          }
      );
    } catch (IOException e) {
      // 3. Convertimos el error de disco en una RuntimeException estándar de Java
      throw new java.io.UncheckedIOException("Error al procesar el archivo temporal en disco", e);
    }
  }

  /**
   * Mapea el resultado sellado del dominio (Pattern Matching) a la respuesta HTTP adecuada.
   */
  private ResponseEntity<IngestionWebResponse> mapToResponse(IngestionResult result) {
    return switch (result) {
      case IngestionResult.Success s -> ResponseEntity.accepted()
          .body(new IngestionWebResponse.Success(s.response()));

      case IngestionResult.InvalidFile e -> ResponseEntity.badRequest()
          .body(new IngestionWebResponse.Failure(
              new ApiError("Archivo inválido", Map.of("reason", e.reason()))
          ));

      case IngestionResult.StorageUnavailable e -> ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
          .body(new IngestionWebResponse.Failure(
              new ApiError("Servicio no disponible", Map.of("debug", e.debugInfo()))
          ));
    };
  }
}