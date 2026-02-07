package com.refiq.platform.ingestion.api.web;

import com.refiq.platform.ingestion.api.dto.IngestionResult;
import com.refiq.platform.ingestion.internal.domain.IngestionFile;
import com.refiq.platform.ingestion.internal.service.IngestionService;

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
 * Controlador REST encargado de la recepción y orquestación inicial de archivos de ingesta (CSVs).
 * <p>
 * Actúa como la capa de entrada (Adapter Primario), transformando las peticiones HTTP (Multipart)
 * en objetos de dominio agnósticos. Su responsabilidad principal es garantizar que el archivo esté
 * disponible físicamente para el procesamiento asíncrono antes de liberar la petición HTTP.
 */
@RestController
@RequestMapping("/api/ingestion")
@RequiredArgsConstructor
public class IngestionController {

  private static final Logger log = LoggerFactory.getLogger(IngestionController.class);

  private final IngestionService ingestionService;

  /**
   * Endpoint principal para la carga de archivos CSV.
   * <p>
   * Implementa el patrón "Fire-and-Forget" (Dispara y Olvida):
   * <ol>
   * <li>Recibe el archivo y valida que no esté vacío.</li>
   * <li>Persiste el archivo en disco temporalmente (para sobrevivir al cierre del request).</li>
   * <li>Delega el procesamiento al servicio de dominio (asíncrono).</li>
   * <li>Retorna inmediatamente un 202 ACCEPTED.</li>
   * </ol>
   *
   * @param file El archivo CSV recibido como `multipart/form-data`.
   * @return {@link ResponseEntity} con el estado de la operación:
   * <ul>
   * <li>202 ACCEPTED: Archivo recibido y encolado correctamente.</li>
   * <li>400 BAD REQUEST: Archivo vacío o error de I/O al guardarlo.</li>
   * <li>503 SERVICE UNAVAILABLE: Fallo crítico en el sistema de almacenamiento.</li>
   * </ul>
   */
  @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<?> upload(@RequestParam("file") MultipartFile file) {

    if (file.isEmpty()) {
      return ResponseEntity.badRequest().body(Map.of("error", "El archivo está vacío."));
    }

    try {

      IngestionFile domainFile = mapToSafeDomainFile(file);

      IngestionResult result = ingestionService.ingest(domainFile);

      return mapToResponse(result);

    } catch (IOException e) {
      log.error("Error I/O en la capa web al procesar archivo temporal", e);
      return ResponseEntity.badRequest()
          .body(Map.of("error", "Error al procesar el archivo temporal."));
    }
  }

  // -------------------------------------------------------------------------
  // HELPER METHODS
  // -------------------------------------------------------------------------

  /**
   * Transforma el {@link MultipartFile} de Spring en un {@link IngestionFile} del dominio.
   * <p>
   * <strong>¿Por qué copiamos el archivo?</strong><br>
   * El {@code MultipartFile} suele ser un stream temporal asociado al ciclo de vida de la petición
   * HTTP. Como el procesamiento se hará en otro hilo (Virtual Thread) después de que la respuesta
   * HTTP se haya enviado, necesitamos volcar el contenido a un archivo físico temporal propio para
   * evitar errores de "Stream Closed".
   * </p>
   * <p>
   * Además, inyectamos el comportamiento de limpieza (cleanup callback) para que el Servicio sepa
   * cómo borrar este archivo físico una vez termine su trabajo.
   * </p>
   *
   * @param file El archivo multipart original.
   * @return Un objeto de dominio seguro con referencia al archivo físico y su lógica de borrado.
   * @throws IOException Si falla la escritura en el disco temporal.
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

      case IngestionResult.InvalidFile e -> ResponseEntity.badRequest()
          .body(Map.of("error", "Archivo inválido", "reason", e.reason()));

      case IngestionResult.StorageUnavailable e ->
          ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
              .body(Map.of("error", "Servicio no disponible", "debug", e.debugInfo()));
    };
  }
}