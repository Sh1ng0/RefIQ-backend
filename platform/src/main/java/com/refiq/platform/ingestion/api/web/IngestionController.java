package com.refiq.platform.ingestion.api.web;

import com.refiq.platform.ingestion.api.dto.IngestionResult;
import com.refiq.platform.ingestion.internal.domain.IngestionFile;
import com.refiq.platform.ingestion.internal.service.IngestionService;
import java.io.IOException;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Controlador REST encargado de gestionar la entrada y procesamiento inicial de archivos.
 * <p>
 * Actúa como punto de entrada (Adapter primario) para la ingesta de datos,
 * transformando las peticiones HTTP en objetos de dominio antes de pasarlos
 * a la capa de servicio.
 * </p>
 */
@RestController
@RequestMapping("/api/ingestion")
@RequiredArgsConstructor
public class IngestionController {

  private final IngestionService ingestionService;

  /**
   * Recibe un archivo a través de una petición multipart, lo valida y delega su procesamiento.
   *
   * @param file El archivo binario recibido en la petición (MultipartFile).
   * @return {@link ResponseEntity} con el resultado de la operación:
   * <ul>
   * <li>200 OK: Si la ingesta fue aceptada/exitosa.</li>
   * <li>400 Bad Request: Si el archivo está vacío, es inválido o hubo error de I/O.</li>
   * <li>503 Service Unavailable: Si el almacenamiento subyacente falla.</li>
   * </ul>
   */
  @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<?> upload(@RequestParam("file") MultipartFile file) {

    if (file.isEmpty()) {
      return ResponseEntity.badRequest().body(Map.of("error", "El archivo está vacío."));
    }

    try {
      // Transformación a dominio (Hexagonal: Adapter -> Domain)
      IngestionFile domainFile = mapToDomain(file);

      // Ejecución de lógica de negocio
      IngestionResult result = ingestionService.ingest(domainFile);

      // Mapeo de vuelta a respuesta HTTP
      return mapToResponse(result);

    } catch (IOException e) {
      // Capturamos errores de bajo nivel en la lectura del stream inicial
      return ResponseEntity.badRequest().body(Map.of("error", "Error de lectura I/O al procesar el archivo."));
    }
  }

  // -------------------------------------------------------------------------
  // HELPER METHODS (Mappers)
  // -------------------------------------------------------------------------

  /**
   * Convierte el archivo de Spring (Multipart) a nuestro objeto de dominio.
   * <p>
   * <strong>Nota importante:</strong> Este método pasa el {@code InputStream} abierto.
   * Es responsabilidad del consumidor (el Servicio o el Adapter de salida) cerrar dicho stream.
   * </p>
   *
   * @param file Archivo multipart de origen.
   * @return Objeto de dominio {@link IngestionFile}.
   * @throws IOException Si falla la obtención del stream de entrada.
   */
  private IngestionFile mapToDomain(MultipartFile file) throws IOException {
    return new IngestionFile(
        file.getOriginalFilename(),
        file.getInputStream(),
        file.getSize(),
        file.getContentType()
    );
  }

  /**
   * Traduce el resultado del dominio (sealed interface) a una respuesta HTTP apropiada.
   * Utiliza Pattern Matching for switch (Java 21+) para exhaustividad.
   *
   * @param result El resultado devuelto por el servicio de dominio.
   * @return La respuesta HTTP mapeada con su status code correspondiente.
   */
  private ResponseEntity<?> mapToResponse(IngestionResult result) {
    return switch (result) {
      case IngestionResult.Success s -> ResponseEntity.ok(s.response());

      case IngestionResult.InvalidFile e -> ResponseEntity.badRequest()
          .body(Map.of("error", "Archivo inválido", "reason", e.reason()));

      case IngestionResult.StorageUnavailable e -> ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
          .body(Map.of("error", "Servicio no disponible", "debug", e.debugInfo()));
    };
  }
}