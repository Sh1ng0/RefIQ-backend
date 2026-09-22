package com.refiq.platform.ingestion.internal.adapter.s3;

import com.refiq.platform.ingestion.internal.port.StoragePort;
import java.util.List;
import java.util.Map;
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
import software.amazon.awssdk.services.s3.model.UploadPartRequest;

/**
 * Secondary adapter implementing file persistence using AWS S3.
 * <p>
 * Fulfills the {@link StoragePort} contract, handling low-level interactions with the S3 SDK,
 * resource management (stream closing), and multipart upload orchestration. Since MinIO implements
 * the same standard as AWS S3, this adapter is fully compatible with MinIO environments without
 * modification.
 * </p>
 */
@Component
public class S3StorageAdapter implements StoragePort {

  private static final Logger log = LoggerFactory.getLogger(S3StorageAdapter.class);

  private final S3Client s3Client;

  @Value("${refiq.storage.s3.bucket-name}")
  private String bucketName;

  public S3StorageAdapter(S3Client s3Client) {
    this.s3Client = s3Client;
  }

  // --- Multipart Methods (Streaming) ---

  /**
   * {@inheritDoc}
   */
  @Override
  public String initMultipartUpload(String key, String contentType, Map<String, String> metadata) {
    try {
      CreateMultipartUploadRequest request = CreateMultipartUploadRequest.builder()
          .bucket(bucketName)
          .key(key)
          .contentType(contentType)
          .metadata(metadata)
          .build();

      String uploadId = s3Client.createMultipartUpload(request).uploadId();

      new StorageLogEvent.MultipartInitiated(uploadId).log(log);

      return uploadId;
    } catch (Exception e) {
      throw new RuntimeException("Error initiating multipart upload: " + e.getMessage(), e);
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

      // AWS SDK v2 uses RequestBody.fromBytes for in-memory arrays
      String eTag = s3Client.uploadPart(request, RequestBody.fromBytes(payload)).eTag();

      return eTag;

    } catch (Exception e) {
      throw new RuntimeException("Error uploading part " + partNumber + ": " + e.getMessage(), e);
    }
  }

  /**
   * {@inheritDoc}
   * <p>
   * <strong>Note:</strong> S3 strictly requires the list of completed parts to be sorted by
   * part number in ascending order. This implementation sorts the provided map before sending the
   * request.
   * </p>
   */
  @Override
  public void completeMultipartUpload(String key, String uploadId,
      Map<Integer, String> completedPartsMap) {
    try {
      // IMPORTANT: S3 requires the list to be sorted by part number (ascending)
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
      new StorageLogEvent.MultipartCompleted(key).log(log);

    } catch (Exception e) {
      throw new RuntimeException("Error completing multipart upload: " + e.getMessage(), e);
    }
  }

  /**
   * {@inheritDoc}
   * <p>
   * Implemented as a "Best Effort" operation. If the abort fails (e.g., network issue), it logs the
   * error but suppresses the exception to avoid masking the original failure cause.
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
      new StorageLogEvent.MultipartAborted(key, uploadId).log(log);

    } catch (Exception e) {
      new StorageLogEvent.AbortFailed(e.getMessage()).log(log);
    }
  }
}