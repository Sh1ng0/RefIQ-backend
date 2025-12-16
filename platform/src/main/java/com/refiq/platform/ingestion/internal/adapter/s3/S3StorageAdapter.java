package com.refiq.platform.ingestion.internal.adapter.s3;

import static org.springframework.cglib.core.CollectionUtils.bucket;

import com.refiq.platform.ingestion.internal.domain.IngestionFile;
import com.refiq.platform.ingestion.internal.port.StoragePort;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.AbortMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompletedMultipartUpload;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.io.InputStream;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;

/**
 * Adaptador de infraestructura (Adapter Secundario) que implementa la persistencia de archivos en
 * AWS S3.
 * <p>
 * Esta clase concreta el puerto {@link StoragePort} definido en el dominio, encargándose de los
 * detalles de bajo nivel de la comunicación con Amazon S3 y la gestión correcta de los recursos de
 * I/O (streams).
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
   * cerrar el {@link InputStream} contenido en el objeto {@code IngestionFile}. Utiliza un bloque
   * <em>try-with-resources</em> para garantizar que no queden file descriptors abiertos, incluso
   * si la subida a S3 falla.
   * </p>
   *
   * @param file      Objeto de dominio que contiene los metadatos y el stream de datos del
   *                  archivo.
   * @param uniqueKey Identificador único (path/key) con el que se guardará el objeto en S3.
   * @return La clave única (key) del objeto almacenado, confirmando la ubicación del recurso.
   * @throws RuntimeException Si ocurre un error de I/O al leer el stream o si la comunicación con
   *                          AWS S3 falla (por credenciales, red, permisos, etc.).
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

      throw new RuntimeException("Error de comunicación con S3: " + e.getMessage(), e);
    }
  }

  // --- Métodos Multipart (Streaming) ---

  /**
   * Inicia una transacción de carga multiparte (Multipart Upload) en S3.
   * <p>
   * Esta operación no sube datos, solo reserva el contexto en S3 y obtiene un ID único
   * que deberá ser utilizado en todas las subidas de partes subsiguientes.
   * </p>
   *
   * @param key Ruta/Nombre del archivo destino en el bucket.
   * @param contentType Tipo MIME del archivo (ej. text/csv).
   * @return El {@code uploadId} generado por AWS S3 para esta transacción específica.
   * @throws RuntimeException Si AWS rechaza la creación de la transacción.
   */
  @Override
  public String initMultipartUpload(String key, String contentType) {
    try {
      CreateMultipartUploadRequest request = CreateMultipartUploadRequest.builder().
          bucket(bucketName)
          .key(key).
          contentType(contentType).build();

      String uploadId = s3Client.createMultipartUpload(request).uploadId();

      return uploadId;
    } catch (Exception e) {
      throw new RuntimeException("Error iniciando multipart upload: " + e.getMessage(), e);
    }

  }

  /**
   * Sube un fragmento (chunk) individual del archivo.
   * <p>
   * Utiliza {@code RequestBody.fromBytes} para enviar el payload en memoria.
   * Es crucial que cada parte (excepto la última) tenga un tamaño mínimo (generalmente 5MB)
   * para que S3 la acepte.
   * </p>
   *
   * @param key La clave del archivo en S3.
   * @param uploadId El ID de la transacción multipart activa.
   * @param partNumber El número secuencial de la parte (comenzando en 1).
   * @param payload El array de bytes con el contenido del fragmento.
   * @return El {@code ETag} (hash) que S3 asigna a esta parte, necesario para completar la unión final.
   * @throws RuntimeException Si falla la subida de la parte específica.
   */
  @Override
  public String uploadPart(String key, String uploadId, int partNumber, byte[] payload) {
    try {
      UploadPartRequest request = UploadPartRequest.builder()
          .bucket(bucketName)
          .key(key)
          .uploadId(uploadId)
          .partNumber(partNumber)
          .build();

      // AWS SDK v2 usa RequestBody.fromBytes para arrays en memoria
      String eTag = s3Client.uploadPart(request, RequestBody.fromBytes(payload)).eTag();

      log.trace("Parte {} subida. Key: {}, ETag: {}", partNumber, key, eTag);
      return eTag;

    } catch (Exception e) {

      throw new RuntimeException("Error subiendo parte " + partNumber + ": " + e.getMessage(), e);
    }

  }

  /**
   * Finaliza la transacción, indicando a S3 que ensamble todas las partes subidas.
   * <p>
   * <strong>Nota Técnica:</strong> S3 exige estrictamente que la lista de partes enviada
   * en esta petición esté ordenada ascendentemente por {@code partNumber}. Este método
   * se encarga de realizar dicho ordenamiento antes de enviar la solicitud.
   * </p>
   *
   * @param key La clave del archivo.
   * @param uploadId El ID de la transacción.
   * @param completedPartsMap Mapa conteniendo {@code partNumber -> ETag} de todas las partes exitosas.
   * @throws RuntimeException Si S3 no puede ensamblar el archivo (ej. faltan partes o ETags inválidos).
   */
  @Override
  public void completeMultipartUpload(String key, String uploadId,
      Map<Integer, String> completedPartsMap) {
    try {
      // IMPORTANTE: S3 exige que la lista esté ordenada por número de parte (ascending)
      List<CompletedPart> awsParts = completedPartsMap.entrySet().stream()
          .sorted(Map.Entry.comparingByKey())
          .map(entry -> CompletedPart.builder()
              .partNumber(entry.getKey())
              .eTag(entry.getValue())
              .build())
          .toList();

      CompletedMultipartUpload completedMultipartUpload = CompletedMultipartUpload.builder()
          .parts(awsParts)
          .build();

      CompleteMultipartUploadRequest request = CompleteMultipartUploadRequest.builder()
          .bucket(bucketName)
          .key(key)
          .uploadId(uploadId)
          .multipartUpload(completedMultipartUpload)
          .build();

      s3Client.completeMultipartUpload(request);
      log.info("Multipart upload completado exitosamente. Key: {}", key);

    } catch (Exception e) {
      throw new RuntimeException("Error finalizando multipart upload: " + e.getMessage(), e);
    }

    }

  /**
   * Cancela una transacción multipart en curso y solicita a S3 que elimine las partes parciales subidas.
   * <p>
   * Se implementa como una operación "Best Effort" (Mejor Esfuerzo): si falla (por red o porque
   * ya no existe el ID), se loguea el error pero no se lanza excepción para no ocultar la causa
   * raíz del fallo original (Rollback silencioso).
   * </p>
   *
   * @param key La clave del archivo.
   * @param uploadId El ID de la transacción a abortar.
   */
  @Override
  public void abortMultipartUpload(String key, String uploadId) {
    try {
      AbortMultipartUploadRequest request = AbortMultipartUploadRequest.builder()
          .bucket(bucketName)
          .key(key)
          .uploadId(uploadId)
          .build();

      s3Client.abortMultipartUpload(request);
      log.warn("Multipart upload abortado. Key: {}, ID: {}", key, uploadId);

    } catch (Exception e) {
      // Solo logueamos, no relanzamos porque suele ser una operación de limpieza
      log.error("Fallo al intentar abortar la subida: {}", e.getMessage());
    }
  }



}


