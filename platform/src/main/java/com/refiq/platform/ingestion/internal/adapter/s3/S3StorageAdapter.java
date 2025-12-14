package com.refiq.platform.ingestion.internal.adapter.s3;

import com.refiq.platform.ingestion.internal.domain.IngestionFile;
import com.refiq.platform.ingestion.internal.port.StoragePort;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.io.InputStream;

/**
 * Adaptador de infraestructura (Adapter Secundario) que implementa la persistencia de archivos en AWS S3.
 * <p>
 * Esta clase concreta el puerto {@link StoragePort} definido en el dominio,
 * encargándose de los detalles de bajo nivel de la comunicación con Amazon S3
 * y la gestión correcta de los recursos de I/O (streams).
 * </p>
 */
@Component
@RequiredArgsConstructor
public class S3StorageAdapter implements StoragePort {

  private static final Logger log = LoggerFactory.getLogger(S3StorageAdapter.class);

  private final S3Client s3Client;

  @Value("${refiq.storage.s3.bucket-name}")
  private String bucketName;

  /**
   * Sube el contenido de un archivo de dominio a un bucket de S3.
   * <p>
   * <strong>Gestión de Recursos:</strong> Este método asume la responsabilidad de
   * cerrar el {@link InputStream} contenido en el objeto {@code IngestionFile}.
   * Utiliza un bloque <em>try-with-resources</em> para garantizar que no queden
   * file descriptors abiertos, incluso si la subida a S3 falla.
   * </p>
   *
   * @param file      Objeto de dominio que contiene los metadatos y el stream de datos del archivo.
   * @param uniqueKey Identificador único (path/key) con el que se guardará el objeto en S3.
   * @return La clave única (key) del objeto almacenado, confirmando la ubicación del recurso.
   * @throws RuntimeException Si ocurre un error de I/O al leer el stream o si la comunicación
   * con AWS S3 falla (por credenciales, red, permisos, etc.).
   */
  @Override
  public String upload(IngestionFile file, String uniqueKey) {

    PutObjectRequest request = PutObjectRequest.builder()
        .bucket(bucketName)
        .key(uniqueKey)
        .contentType(file.contentType())
        .build();

    // Aquí es donde garantizamos que el stream (abierto en el Controller) se cierra.
    // El try-with-resources asegura el .close() automático al terminar el bloque.
    try (InputStream streamToClose = file.content()) {

      // AWS SDK v2 requiere conocer el tamaño para optimizar la transferencia (Content-Length)
      RequestBody body = RequestBody.fromInputStream(streamToClose, file.size());

      s3Client.putObject(request, body);

      log.debug("Subida a S3 exitosa: {}/{}", bucketName, uniqueKey);

      return uniqueKey;

    } catch (IOException e) {
      throw new RuntimeException("Error de I/O gestionando el stream del archivo local", e);
    } catch (Exception e) {
      // Capturamos S3Exception u otras RuntimeExceptions del SDK
      throw new RuntimeException("Error de comunicación con S3: " + e.getMessage(), e);
    }
  }
}