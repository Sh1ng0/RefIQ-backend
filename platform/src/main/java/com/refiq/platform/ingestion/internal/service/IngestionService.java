package com.refiq.platform.ingestion.internal.service;

import com.opencsv.CSVParser;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.CSVWriter;
import com.refiq.platform.ingestion.api.dto.IngestionResponse;
import com.refiq.platform.ingestion.api.dto.IngestionResult;
import com.refiq.platform.ingestion.api.dto.Status;
import com.refiq.platform.ingestion.internal.domain.IngestionFile;
import com.refiq.platform.ingestion.internal.normalizer.CanonicalCSV;
import com.refiq.platform.ingestion.internal.normalizer.CsvNormalizer;
import com.refiq.platform.ingestion.internal.normalizer.NormalizationResult;
import com.refiq.platform.ingestion.internal.port.StoragePort;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Service responsible for the core ingestion pipeline logic.
 * <p>
 * It orchestrates the flow:
 * <ol>
 * <li>Receives the raw file.</li>
 * <li>Uploads the raw content to Storage (S3).</li>
 * <li>Detects CSV format (separator auto-discovery).</li>
 * <li>Normalizes content row-by-row to the Canonical format.</li>
 * <li>Streams the normalized content back to Storage using Multipart Upload.</li>
 * </ol>
 * <p>
 * <strong>concurrency & Backpressure:</strong>
 * This service uses Virtual Threads to handle I/O-intensive tasks efficiently.
 * To prevent Out-Of-Memory (OOM) errors with large files, it implements a {@link Semaphore}-based
 * backpressure mechanism that limits the number of concurrent chunk uploads to S3.
 * </p>
 */
@Service
@RequiredArgsConstructor
public class IngestionService {

  private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

  // Buffer settings for Multipart Upload
  private static final int MIN_PART_SIZE_BYTES = 5 * 1024 * 1024; // 5MB
  private static final int BUFFER_SAFETY_MARGIN = 64 * 1024;
  private static final long UPLOAD_TIMEOUT_MINUTES = 60;

  // SEMAPHORE: Backpressure Control
  // Limit to 4 concurrent uploads (approx 20MB retained memory)
  private final Semaphore uploadPermits = new Semaphore(4);

  private final StoragePort storagePort;
  private final CsvNormalizer normalizer;

  // Virtual Threads Executor
  private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

  /**
   * Initiates the ingestion process asynchronously.
   *
   * @param file The domain file object to be processed.
   * @return A {@link IngestionResult.Success} containing the tracking ID and initial status.
   */
  public IngestionResult ingest(IngestionFile file) {
    UUID fileId = UUID.randomUUID();

    String rawKey = "raw/" + fileId + "-" + file.filename();
    String canonicalKey = "canonical/" + fileId + ".csv";

    IngestionLogEvent.UPLOAD_INITIATED.log(log, file.filename(), file.size());

    // Launch full process in a virtual thread (Fire-and-forget)
    executor.submit(() -> processPipelineAsync(file, fileId, rawKey, canonicalKey));

    return new IngestionResult.Success(
        new IngestionResponse(fileId, Status.PENDING_PROCESSING)
    );
  }

  /**
   * Orchestration pipeline: 1. Upload Raw to S3. 2. If successful, normalize and upload Canonical to S3.
   */
  private void processPipelineAsync(IngestionFile file, UUID fileId, String rawKey, String canonicalKey) {
    try {
      IngestionLogEvent.RAW_UPLOAD_STARTED.log(log, rawKey);

      // Raw upload (sequential, no semaphore needed here)
      storagePort.upload(file, rawKey);

      IngestionLogEvent.RAW_UPLOAD_SUCCESS.log(log, rawKey);

      processCanonicalMultipart(file, fileId, canonicalKey);

    } catch (Exception e) {
      IngestionLogEvent.PIPELINE_ERROR.log(log, fileId, e.getMessage());
      // Logic to decide whether to delete RAW or keep for auditing could go here
    } finally {
      if (file.cleanupCallback() != null) {
        file.cleanupCallback().run();
      }
    }
  }

  private void processCanonicalMultipart(IngestionFile file, UUID fileId, String key) {
    String uploadId = null;
    Map<Integer, String> completedParts = new ConcurrentHashMap<>();
    var uploadTasks = new ArrayList<CompletableFuture<Void>>();

    long totalRows = 0;
    long validRows = 0;
    long failedRows = 0;

    IngestionLogEvent.CANONICAL_PROCESSING_STARTED.log(log, key);

    char detectedSeparator = detectSeparator(file, fileId); // Updated signature to pass ID for logging

    CSVParser parser = new CSVParserBuilder()
        .withSeparator(detectedSeparator)
        .withIgnoreQuotations(false)
        .build();

    try (
        InputStreamReader isr = new InputStreamReader(file.openStream(), StandardCharsets.UTF_8);
        CSVReader csvReader = new CSVReaderBuilder(isr).withCSVParser(parser).build();

        ByteArrayOutputStream buffer = new ByteArrayOutputStream(MIN_PART_SIZE_BYTES + BUFFER_SAFETY_MARGIN);
        OutputStreamWriter bufferWriter = new OutputStreamWriter(buffer, StandardCharsets.UTF_8);
        CSVWriter csvWriter = new CSVWriter(bufferWriter)
    ) {

      uploadId = storagePort.initMultipartUpload(key, "text/csv");
      IngestionLogEvent.MULTIPART_INITIATED.log(log, uploadId);

      int partNumber = 1;

      // Write Canonical Header
      csvWriter.writeNext(CanonicalCSV.HEADERS);

      String[] dirtyRow;
      boolean isFirstLine = true;

      while ((dirtyRow = csvReader.readNext()) != null) {

        if (isFirstLine) {
          isFirstLine = false;
          // Strict header validation logic would go here
          continue;
        }

        totalRows++;
        NormalizationResult result = normalizer.normalize(dirtyRow);

        switch (result) {
          case NormalizationResult.Success success -> {
            csvWriter.writeNext(success.data().toCsvRow());
            validRows++;
          }
          case NormalizationResult.Failure failure -> {
            failedRows++;
            // Limit log noise for massive files
            if (failedRows <= 10) {
              IngestionLogEvent.ROW_REJECTED.log(log, failure.reason(), String.join("|", failure.originalRow()));
            }
          }
          case NormalizationResult.Ignored ignored -> { /* No-op */ }
        }

        csvWriter.flush();

        // If buffer exceeds limit (5MB), upload chunk
        if (buffer.size() >= MIN_PART_SIZE_BYTES) {
          byte[] chunkPayload = buffer.toByteArray();

          CompletableFuture<Void> task = uploadChunkWithBackpressure(key, uploadId, partNumber, chunkPayload, completedParts);
          uploadTasks.add(task);

          partNumber++;
          buffer.reset();
        }
      }

      csvWriter.flush();
      if (buffer.size() > 0) {
        CompletableFuture<Void> task = uploadChunkWithBackpressure(key, uploadId, partNumber, buffer.toByteArray(), completedParts);
        uploadTasks.add(task);
      }

      CompletableFuture.allOf(uploadTasks.toArray(new CompletableFuture[0]))
          .orTimeout(UPLOAD_TIMEOUT_MINUTES, TimeUnit.MINUTES)
          .join();

      storagePort.completeMultipartUpload(key, uploadId, completedParts);

      IngestionLogEvent.CANONICAL_SUMMARY.log(log, fileId, totalRows, validRows, failedRows);

    } catch (Exception e) {
      IngestionLogEvent.PIPELINE_ERROR.log(log, fileId, "Canonical processing failed: " + e.getMessage());

      if (uploadId != null) {
        storagePort.abortMultipartUpload(key, uploadId);
        IngestionLogEvent.MULTIPART_ABORTED.log(log, key);
      }

      uploadTasks.forEach(t -> t.cancel(true));

      throw new RuntimeException(e);
    }
  }

  /**
   * Reads the first line to guess the separator.
   * Heuristic: The character appearing most frequently in the header wins.
   */
  private char detectSeparator(IngestionFile file, UUID fileId) {
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.openStream(), StandardCharsets.UTF_8))) {

      String header = reader.readLine();

      if (header == null || header.isBlank()) {
        return ';'; // Safe default
      }

      long semicolons = header.chars().filter(ch -> ch == ';').count();
      long commas = header.chars().filter(ch -> ch == ',').count();

      char detected = (commas > semicolons) ? ',' : ';';

      IngestionLogEvent.SEPARATOR_DETECTED.log(log, fileId, detected);
      return detected;

    } catch (Exception e) {
      IngestionLogEvent.SEPARATOR_DETECTION_FAILED.log(log, e.getMessage());
      return ';';
    }
  }

  /**
   * Uploads a chunk managing semaphore for Backpressure.
   */
  private CompletableFuture<Void> uploadChunkWithBackpressure(
      String key, String uploadId, int partNum, byte[] payload, Map<Integer, String> partsMap) {

    try {
      // BLOCKING: If semaphore is full, the READER thread pauses here.
      uploadPermits.acquire();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("Interrupted waiting for upload permit", e);
    }

    return CompletableFuture.runAsync(() -> {
          String eTag = storagePort.uploadPart(key, uploadId, partNum, payload);
          partsMap.put(partNum, eTag);
          // Optional: Log success per part (can be noisy)
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