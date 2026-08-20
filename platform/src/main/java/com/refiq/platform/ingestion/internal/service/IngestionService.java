package com.refiq.platform.ingestion.internal.service;

import com.refiq.platform.ingestion.api.dto.IngestionResponse;
import com.refiq.platform.ingestion.api.dto.IngestionResult;
import com.refiq.platform.ingestion.api.dto.Status;
import com.refiq.platform.ingestion.api.event.FileAcceptedEvent;
import com.refiq.platform.ingestion.api.event.IngestionFailedEvent;
import com.refiq.platform.ingestion.internal.domain.IngestionFile;
import com.refiq.platform.ingestion.internal.port.StoragePort;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Service responsible for orchestrating the asynchronous file ingestion pipeline.
 * <p>
 * This core service handles the receipt of clinical data files, assigns a unique Correlation ID (UUID),
 * and manages the robust, chunked upload to the Data Lake's Bronze layer via the {@link StoragePort}.
 * It operates in a completely decoupled manner, utilizing Spring Modulith events ({@link FileAcceptedEvent}
 * and {@link IngestionFailedEvent}) to communicate state changes to downstream modules (like Calculation)
 * without direct domain coupling.
 * </p>
 * <p>
 * To ensure high performance and low memory footprint, it leverages Java Virtual Threads and applies
 * backpressure mechanisms (Semaphores) during multipart uploads.
 * </p>
 */
@Service
public class IngestionService {

  private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

  private static final int MIN_PART_SIZE_BYTES = 5 * 1024 * 1024;
  private static final long UPLOAD_TIMEOUT_MINUTES = 60;

  private final Semaphore uploadPermits = new Semaphore(4);

  /**
   * Semaphore used to enforce backpressure. It limits the number of concurrent chunk uploads
   * to prevent memory exhaustion and network congestion when dealing with large files.
   */
  private final StoragePort storagePort;
  private final ExecutorService ingestionExecutor;

  private final RestClient restClient;
  private final String dataLakeApiUrl;
  private final ApplicationEventPublisher eventPublisher;

  public IngestionService(StoragePort storagePort,
      @Value("${refiq.datalake.api.url:http://refiq-pipeline-api:8001}") String dataLakeApiUrl,
      ApplicationEventPublisher eventPublisher,
      ExecutorService ingestionExecutor) {

    this.storagePort = storagePort;
    this.dataLakeApiUrl = dataLakeApiUrl;
    this.restClient = RestClient.create();
    this.eventPublisher = eventPublisher;
    this.ingestionExecutor = ingestionExecutor;
  }

  /**
   * Initiates the ingestion process for a provided clinical data file.
   * <p>
   * This method performs the synchronous initial steps: generating the UUID, constructing the S3 target key,
   * and publishing the initial domain event so tracking can begin. The heavy lifting (uploading) is immediately
   * delegated to a background Virtual Thread, allowing this method to return an HTTP 202 Accepted equivalent rapidly.
   * </p>
   *
   * @param file The domain object representing the uploaded file and its metadata.
   * @return An {@link IngestionResult.Success} containing the generated UUID and a PENDING status.
   */
  public IngestionResult ingest(IngestionFile file) {
    UUID fileId = UUID.randomUUID();

    String targetKey = "1.Bronze/" + file.analyte().name() + "/" + file.analyte().name() + "_" + fileId + ".csv";


    eventPublisher.publishEvent(new FileAcceptedEvent(fileId, file.analyte().name()));

    IngestionLogEvent.UPLOAD_INITIATED.log(log, file.filename(), file.analyte().name(), file.size());

    ingestionExecutor.submit(() -> processBronzeMultipart(file, fileId, targetKey));

    return new IngestionResult.Success(
        new IngestionResponse(fileId, Status.PENDING_PROCESSING)
    );
  }

  /**
   * Executes the chunked multipart upload to the Bronze layer in the background.
   * <p>
   * Reads the incoming stream in chunks (parts), uploading them asynchronously. If the entire process
   * is successful, it commands the storage port to assemble the parts and triggers the external Data Lake.
   * If any network error or timeout occurs, it safely aborts the multipart transaction in S3/MinIO,
   * cancels pending tasks, and publishes an {@link IngestionFailedEvent} to rollback tracking states.
   * </p>
   *
   * @param file   The ingestion file containing the data stream and cleanup callbacks.
   * @param fileId The generated Correlation ID for this transaction.
   * @param key    The computed destination path (S3 Key) in the Bronze layer.
   */
  private void processBronzeMultipart(IngestionFile file, UUID fileId, String key) {
    String uploadId = null;
    Map<Integer, String> completedParts = new ConcurrentHashMap<>();
    var uploadTasks = new ArrayList<CompletableFuture<Void>>();

    Map<String, String> metadata = Map.of(
        "original-filename", file.filename(),
        "analyte", file.analyte().name(),
        "record-id", fileId.toString()
    );

    try (InputStream is = file.openStream()) {
      uploadId = storagePort.initMultipartUpload(key, file.contentType(), metadata);
      IngestionLogEvent.BRONZE_PROCESSING_STARTED.log(log, key);

      int partNumber = 1;
      byte[] chunkPayload;

      while ((chunkPayload = is.readNBytes(MIN_PART_SIZE_BYTES)).length > 0) {
        CompletableFuture<Void> task = uploadChunkWithBackpressure(key, uploadId, partNumber,
            chunkPayload, completedParts);
        uploadTasks.add(task);
        partNumber++;
      }

      CompletableFuture.allOf(uploadTasks.toArray(new CompletableFuture[0]))
          .orTimeout(UPLOAD_TIMEOUT_MINUTES, TimeUnit.MINUTES)
          .join();

      storagePort.completeMultipartUpload(key, uploadId, completedParts);
      IngestionLogEvent.BRONZE_SUMMARY.log(log, fileId, partNumber - 1);

      triggerDataLakePipeline();

    } catch (Exception e) {
      IngestionLogEvent.PIPELINE_ERROR.log(log, fileId, e.getMessage());

      // 2. PUBLICAMOS EL EVENTO DE FALLO (En lugar de ensuciarnos las manos con la BD)
      eventPublisher.publishEvent(new IngestionFailedEvent(fileId, "Error en la subida a MinIO: " + e.getMessage()));

      if (uploadId != null) {
        storagePort.abortMultipartUpload(key, uploadId);
      }
      uploadTasks.forEach(t -> t.cancel(true));
      throw new RuntimeException(e);

    } finally {
      if (file.cleanupCallback() != null) {
        file.cleanupCallback().run();
      }
    }
  }

  /**
   * Uploads a single data chunk to the storage port while enforcing concurrency limits (backpressure).
   * <p>
   * A permit must be acquired from the {@code uploadPermits} Semaphore before the asynchronous upload begins.
   * Once the upload completes (successfully or exceptionally), the permit is safely released.
   * </p>
   *
   * @param key      The destination path (S3 Key).
   * @param uploadId The active multipart transaction ID.
   * @param partNum  The sequential number of this chunk (1-indexed).
   * @param payload  The byte array containing the chunk's data.
   * @param partsMap A concurrent map to store the returned ETag upon successful upload.
   * @return A {@link CompletableFuture} representing the asynchronous upload task.
   */
  private CompletableFuture<Void> uploadChunkWithBackpressure(
      String key, String uploadId, int partNum, byte[] payload, Map<Integer, String> partsMap) {

    try {
      uploadPermits.acquire();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("Interrupted waiting for upload permit", e);
    }

    return CompletableFuture.runAsync(() -> {
          String eTag = storagePort.uploadPart(key, uploadId, partNum, payload);
          partsMap.put(partNum, eTag);
        }, ingestionExecutor)
        .whenComplete((res, ex) -> {
          uploadPermits.release();
          if (ex != null) {
            IngestionLogEvent.MULTIPART_PART_FAILED.log(log, partNum, ex.getMessage());
          }
        });
  }

  /**
   * Triggers the external Data Lake pipeline via a REST call.
   * <p>
   * This is a "fire-and-forget" mechanism. If the Data Lake API is temporarily unreachable or returns an error,
   * the exception is caught and logged, but it does not roll back the successful S3 upload or crash the application.
   * </p>
   */
  private void triggerDataLakePipeline() {
    try {
      restClient.post()
          .uri(dataLakeApiUrl + "/run-pipeline")
          .retrieve()
          .toBodilessEntity();
      IngestionLogEvent.DATALAKE_TRIGGER_SENT.log(log);
    } catch (Exception e) {
      IngestionLogEvent.DATALAKE_TRIGGER_FAILED.log(log, e.getMessage());
    }
  }
}