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
 * Secondary adapter implementing file persistence using AWS S3.
 * <p>
 * This class fulfills the {@link StoragePort} contract, handling low-level interactions with
 * the S3 SDK, resource management (stream closing), and multipart upload orchestration.
 * </p>
 */
@Component
@RequiredArgsConstructor
public class S3StorageAdapter implements StoragePort {

  private static final Logger log = LoggerFactory.getLogger(S3StorageAdapter.class);

  private final S3Client s3Client;

  @Value("${refiq.storage.s3.bucket-name}")
  private String bucketName;


  @Override
  @Deprecated
  public String upload(IngestionFile file, String uniqueKey) {

    PutObjectRequest request = PutObjectRequest.builder()
        .bucket(bucketName)
        .key(uniqueKey)
        .contentType(file.contentType())
        .build();

    // Aquí es donde garantizamos que el stream (abierto en el Controller) se cierra.
    // El try-with-resources asegura el .close() automático al terminar el bloque.
    try (InputStream streamToClose = file.openStream()) {

      // AWS SDK v2 requiere conocer el tamaño para optimizar la transferencia (Content-Length)
      RequestBody body = RequestBody.fromInputStream(streamToClose, file.size());

      s3Client.putObject(request, body);

      StorageLogEvent.SINGLE_UPLOAD_SUCCESS.log(log, bucketName, uniqueKey);

      return uniqueKey;

    } catch (IOException e) {
      throw new RuntimeException("Error de I/O gestionando el stream del archivo local", e);
    } catch (Exception e) {

      throw new RuntimeException("Error de comunicación con S3: " + e.getMessage(), e);
    }
  }

  // --- Métodos Multipart (Streaming) ---

  /**
   * {@inheritDoc}
   */
  @Override
  public String initMultipartUpload(String key, String contentType) {
    try {
      CreateMultipartUploadRequest request = CreateMultipartUploadRequest.builder().
          bucket(bucketName)
          .key(key).
          contentType(contentType).build();

      String uploadId = s3Client.createMultipartUpload(request).uploadId();

      StorageLogEvent.MULTIPART_INITIATED.log(log, uploadId);

      return uploadId;
    } catch (Exception e) {
      throw new RuntimeException("Error iniciando multipart upload: " + e.getMessage(), e);
    }

  }

  /**
   * {@inheritDoc}
   * <p>
   * Uses in-memory byte arrays for the payload. S3 requires parts (except the last one) to be
   * larger than a minimum size (typically 5MB).
   * </p>
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

//      StorageLogEvent.PART_UPLOADED.log(log, partNumber, key, eTag);
      return eTag;

    } catch (Exception e) {

      throw new RuntimeException("Error subiendo parte " + partNumber + ": " + e.getMessage(), e);
    }

  }

  /**
   * {@inheritDoc}
   * <p>
   * <strong>Note:</strong> S3 strictly requires the list of completed parts to be sorted by
   * part number in ascending order. This implementation sorts the provided map before sending
   * the request.
   * </p>
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
      StorageLogEvent.MULTIPART_COMPLETED.log(log, key);

    } catch (Exception e) {
      throw new RuntimeException("Error finalizando multipart upload: " + e.getMessage(), e);
    }

    }

  /**
   * {@inheritDoc}
   * <p>
   * Implemented as a "Best Effort" operation. If the abort fails (e.g., network issue),
   * it logs the error but suppresses the exception to avoid masking the original failure cause.
   * </p>
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
      StorageLogEvent.MULTIPART_ABORTED.log(log, key, uploadId);

    } catch (Exception e) {
      StorageLogEvent.ABORT_FAILED.log(log, e.getMessage());
    }
  }



}


