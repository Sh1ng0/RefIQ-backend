package com.refiq.platform.ingestion.internal.port;


import com.refiq.platform.ingestion.internal.adapter.s3.S3StorageAdapter;
import com.refiq.platform.ingestion.internal.domain.IngestionFile;
import java.util.Map;

/**
 * Output port for file persistence operations.
 * <p>
 * This interface defines the contract for storing ingestion files, decoupling the domain
 * from specific storage technologies (e.g., AWS S3, local filesystem).
 * </p>
 */
public interface StoragePort {


  /**
   * Initiates a multipart upload transaction.
   * <p>
   * This reserves the context in the storage system but does not upload data yet.
   * </p>
   *
   * @param key         The destination path/key for the file.
   * @param contentType The MIME type of the file content.
   * @return A unique upload ID associated with this specific transaction.
   */
  String initMultipartUpload(String key, String contentType, Map<String, String> metadata);


  /**
   * Uploads a specific chunk (part) of the file.
   *
   * @param key        The file key.
   * @param uploadId   The active transaction ID.
   * @param partNumber The sequential number of the part (starting from 1).
   * @param payload    The byte array containing the chunk data.
   * @return The ETag (entity tag) assigned by the storage system to this part, required for final assembly.
   */
  String uploadPart (String key, String uploadId, int partNumber, byte[] payload);

  /**
   * Finalizes the multipart transaction, instructing the storage system to assemble all uploaded parts.
   *
   * @param key               The file key.
   * @param uploadId          The transaction ID.
   * @param completedParts A map containing the part numbers and their corresponding ETags.
   */
  void completeMultipartUpload(String key, String uploadId, Map<Integer, String> completedParts);

  /**
   * Aborts an ongoing multipart transaction and requests the deletion of any uploaded parts.
   *
   * @param key      The file key.
   * @param uploadId The transaction ID to abort.
   */
  void abortMultipartUpload (String key, String uploadId);

}
