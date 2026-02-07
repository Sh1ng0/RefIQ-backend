
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

import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class IngestionService {

  private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

  // Ajustes de buffer para Multipart Upload
  private static final int MIN_PART_SIZE_BYTES = 5 * 1024 * 1024;
  private static final int BUFFER_SAFETY_MARGIN = 64 * 1024;
  private static final long UPLOAD_TIMEOUT_MINUTES = 60;

  // Input separator (delimitador que esperamos del Hospital/Cliente)
  private static final char INPUT_SEPARATOR = ';';

  private final StoragePort storagePort;
  private final CsvNormalizer normalizer;


  private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

  public IngestionResult ingest(IngestionFile file) {
    UUID fileId = UUID.randomUUID();

    String rawKey = "raw/" + fileId + "-" + file.filename();
    String canonicalKey = "canonical/" + fileId + ".csv";

    IngestionLogEvent.UPLOAD_INITIATED.log(log, file.filename(), file.size());

    executor.submit(() -> processPipelineAsync(file, fileId, rawKey, canonicalKey));

    return new IngestionResult.Success(
        new IngestionResponse(fileId, Status.PENDING_PROCESSING)
    );
  }

  /**
   * Pipeline orquestador: 1. Sube Raw a S3. 2. Si tiene éxito, normaliza y sube Canonical a S3.
   */
  private void processPipelineAsync(IngestionFile file, UUID fileId, String rawKey,
      String canonicalKey) {
    try {

      IngestionLogEvent.RAW_UPLOAD_STARTED.log(log, rawKey);
      storagePort.upload(file, rawKey);
      IngestionLogEvent.RAW_UPLOAD_SUCCESS.log(log, rawKey);

      processCanonicalMultipart(file, fileId, canonicalKey);

    } catch (Exception e) {
      IngestionLogEvent.PIPELINE_ERROR.log(log, fileId, e.getMessage());

    } finally {

      if (file.cleanupCallback() != null) {
        file.cleanupCallback().run();
        // CLEANUP_EXECUTED(LogLevel.TRACE, "Callback de limpieza de recursos ejecutado.");
        // Maybe add trace level to the logger
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

    // Configuración del Parser para leer ';'
    CSVParser parser = new CSVParserBuilder()
        .withSeparator(INPUT_SEPARATOR)
        .withIgnoreQuotations(false)
        .build();

    IngestionLogEvent.CANONICAL_PROCESSING_STARTED.log(log, key);

    try (

        InputStreamReader isr = new InputStreamReader(file.openStream(), StandardCharsets.UTF_8);
        CSVReader csvReader = new CSVReaderBuilder(isr).withCSVParser(parser).build();

        ByteArrayOutputStream buffer = new ByteArrayOutputStream(
            MIN_PART_SIZE_BYTES + BUFFER_SAFETY_MARGIN);
        OutputStreamWriter bufferWriter = new OutputStreamWriter(buffer, StandardCharsets.UTF_8);

        // Formato estándar (Separador ',' y Quote '"')
        CSVWriter csvWriter = new CSVWriter(bufferWriter)
    ) {

      // Iniciamos transacción Multipart en S3 para el archivo CANÓNICO
      uploadId = storagePort.initMultipartUpload(key, "text/csv");
      IngestionLogEvent.MULTIPART_INITIATED.log(log, uploadId);

      int partNumber = 1;

      // Header Canónico (Contrato estricto para R)
      csvWriter.writeNext(CanonicalCSV.HEADERS);

      String[] dirtyRow;
      boolean isFirstLine = true;

      while ((dirtyRow = csvReader.readNext()) != null) {

        // Saltamos el header del archivo original si existe
        if (isFirstLine) {
          isFirstLine = false;
          // POTENTIAL HEADER VALIDATION GOES HERE
          continue;
        }

        totalRows++;

        // --- CORE LOGIC: Delegación al Normalizer ---
        NormalizationResult result = normalizer.normalize(dirtyRow);

        switch (result) {
          case NormalizationResult.Success success -> {
            csvWriter.writeNext(success.data().toCsvRow());
            validRows++;
          }
          case NormalizationResult.Failure failure -> {
            failedRows++;

            if (failedRows <= 10) {
              IngestionLogEvent.ROW_REJECTED.log(log, failure.reason(),
                  String.join("|", failure.originalRow()));
            }
          }
          case NormalizationResult.Ignored ignored -> {

          }
        }

        // Gestión de buffer (Flush a S3 cuando se llena)
        csvWriter.flush();
        if (buffer.size() >= MIN_PART_SIZE_BYTES) {
          uploadChunk(key, uploadId, partNumber, buffer.toByteArray(), completedParts, uploadTasks);
          partNumber++;
          buffer.reset();
        }
      }

      csvWriter.flush();
      if (buffer.size() > 0) {
        uploadChunk(key, uploadId, partNumber, buffer.toByteArray(), completedParts, uploadTasks);
      }

      // Espera de los vts
      CompletableFuture.allOf(uploadTasks.toArray(new CompletableFuture[0]))
          .orTimeout(UPLOAD_TIMEOUT_MINUTES, TimeUnit.MINUTES)
          .join();

      storagePort.completeMultipartUpload(key, uploadId, completedParts);

      IngestionLogEvent.CANONICAL_SUMMARY.log(log, fileId, totalRows, validRows, failedRows);

    } catch (Exception e) {
      log.error("Error procesando canonical CSV", e);
      if (uploadId != null) {
        storagePort.abortMultipartUpload(key, uploadId);
      }
      throw new RuntimeException(e); // Propagar para loguear arriba
    }
  }

  // --- Helper para subir chunks (Igual que antes) ---
  private void uploadChunk(String key, String uploadId, int partNum, byte[] payload,
      Map<Integer, String> partsMap, List<CompletableFuture<Void>> tasks) {

    CompletableFuture<Void> promise = new CompletableFuture<>();

    var physicalTask = executor.submit(() -> {
      try {
        String eTag = storagePort.uploadPart(key, uploadId, partNum, payload);
        partsMap.put(partNum, eTag);
        promise.complete(null);
      } catch (Exception e) {
        promise.completeExceptionally(e);
      }
    });

    promise.whenComplete((r, ex) -> {
      if (promise.isCancelled()) {
        physicalTask.cancel(true);
      }
    });

    tasks.add(promise);
  }
}