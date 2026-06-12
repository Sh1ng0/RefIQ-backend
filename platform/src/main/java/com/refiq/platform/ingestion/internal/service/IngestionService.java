package com.refiq.platform.ingestion.internal.service;


import com.refiq.platform.ingestion.internal.port.StoragePort;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;


import com.refiq.platform.ingestion.api.dto.IngestionResponse;
import com.refiq.platform.ingestion.api.dto.IngestionResult;
import com.refiq.platform.ingestion.api.dto.Status;
import com.refiq.platform.ingestion.internal.domain.IngestionFile;


import java.io.InputStream;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import com.refiq.platform.ingestion.internal.domain.Analyte;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Service responsible for the core ingestion pipeline logic.
 * <p>
 * It acts as a highly efficient "byte router". It takes the raw file, generates the required
 * routing metadata, and streams the raw bytes directly to the Data Lake (S3) using Multipart
 * Uploads. The file is placed in a specific folder corresponding to its {@link Analyte}.
 * </p>
 * <p>
 * <strong>Concurrency & Backpressure:</strong><br>
 * Uses Virtual Threads for I/O and a {@link Semaphore} to limit concurrent chunk uploads to S3,
 * preventing Out-Of-Memory (OOM) errors on massive files while maximizing network throughput.
 * </p>
 */
@Service
@RequiredArgsConstructor
public class IngestionService {

  private static final Logger log = LoggerFactory.getLogger(IngestionService.class);


  private static final int MIN_PART_SIZE_BYTES = 5 * 1024 * 1024;
  private static final long UPLOAD_TIMEOUT_MINUTES = 60;


  private final Semaphore uploadPermits = new Semaphore(4);

  private final StoragePort storagePort;

  private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();


  /**
   * Initiates the asynchronous routing of the file to the Data Lake.
   * <p>
   * Generates a unique tracking ID and constructs the target S3 path based on the file's
   * analyte (e.g., {@code raw/ALP/ALP_1234.csv}).
   * </p>
   *
   * @param file The validated domain object representing the uploaded file.
   * @return An {@link IngestionResult.Success} containing the tracking UUID.
   */
  public IngestionResult ingest(IngestionFile file) {
    UUID fileId = UUID.randomUUID();

    // Construimos la ruta dinámica: carpeta del analito + nombre único
    // Ejemplo: raw/ALP/ALP_8923-asdf.csv
    String targetKey = "raw/" + file.analyte().name() + "/" + file.analyte().name() + "_" + fileId + ".csv";
    IngestionLogEvent.UPLOAD_INITIATED.log(log, file.filename(), file.analyte().name(), file.size());


    executor.submit(() -> processBronzeMultipart(file, fileId, targetKey));

    return new IngestionResult.Success(
        new IngestionResponse(fileId, Status.PENDING_PROCESSING)
    );
  }

  /**
   * Executes the chunked upload to S3 and injects required routing metadata.
   * <p>
   * The file is read in chunks of {@value MIN_PART_SIZE_BYTES} bytes. S3 Object Metadata
   * is injected to allow downstream Data pipelines to identify the analyte and original
   * filename without downloading the payload.
   * </p>
   *
   * @param file   The domain file.
   * @param fileId The unique UUID assigned to this ingestion.
   * @param key    The computed target S3 key (path).
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

    } catch (Exception e) {
      IngestionLogEvent.PIPELINE_ERROR.log(log, fileId, e.getMessage());

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
   * Uploads a chunk managing semaphore for Backpressure.
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
          // IngestionLogEvent.MULTIPART_PART_UPLOADED.log(log, partNum, eTag);
        }, executor)
        .whenComplete((res, ex) -> {
          uploadPermits.release();
          if (ex != null) {
            IngestionLogEvent.MULTIPART_PART_FAILED.log(log, partNum, ex.getMessage());
          }
        });
  }
}