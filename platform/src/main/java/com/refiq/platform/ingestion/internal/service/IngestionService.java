
package com.refiq.platform.ingestion.internal.service;

import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import com.refiq.platform.ingestion.api.dto.IngestionResponse;
import com.refiq.platform.ingestion.api.dto.IngestionResult;
import com.refiq.platform.ingestion.api.dto.Status;
import com.refiq.platform.ingestion.internal.domain.IngestionFile;
import com.refiq.platform.ingestion.internal.port.StoragePort;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
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


/**
 * Servicio de dominio principal para la orquestación de la ingesta de archivos CSV.
 * <p>
 * Implementa un patrón de procesamiento asíncrono y tolerante a fallos:
 * <ul>
 * <li>Lectura en streaming (sin cargar todo el archivo en RAM).</li>
 * <li>Validación estructural y limpieza de CSV (usando OpenCSV).</li>
 * <li>Carga multipart a S3 en paralelo usando Virtual Threads.</li>
 * <li>Garantía de limpieza de recursos (borrado de temporales) mediante callbacks.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class IngestionService {

  private static final Logger log = LoggerFactory.getLogger(IngestionService.class);


  private static final int MIN_PART_SIZE_BYTES = 5 * 1024 * 1024;

  private static final int BUFFER_SAFETY_MARGIN = 64 * 1024;

  private static final long UPLOAD_TIMEOUT_MINUTES = 60;

  private final StoragePort storagePort;


  private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

  /**
   * Punto de entrada principal. Recibe el archivo, asigna un ID de seguimiento y delega
   * el procesamiento a un hilo virtual en segundo plano.
   *
   * @param file Archivo agnóstico de infraestructura (desacoplado de HTTP/Multipart).
   * @return Un resultado sellado (Success o Failure) que permite al controlador responder inmediatamente (202 Accepted).
   */
  public IngestionResult ingest(IngestionFile file) {
    UUID fileId = UUID.randomUUID();
    String storageKey = fileId + "-" + file.filename();

    IngestionLogEvent.UPLOAD_INITIATED.log(log, file.filename(), file.size());


    executor.submit(() -> processFileAsync(file, fileId, storageKey));

    // Retornamos  "ACCEPTED" mientras el proceso asíncrono trabaja (HTTP 202 conceptual)
    IngestionLogEvent.ASYNC_PROCESS_STARTED.log(log, fileId, storageKey);

    return new IngestionResult.Success(
        new IngestionResponse(fileId, Status.PENDING_PROCESSING)
    );
  }

  /**
   * Núcleo del procesamiento (Ejecutado en Virtual Thread).
   * <p>
   * Realiza un pipeline de ETL ligero:
   * <ol>
   * <li>Lee el CSV línea a línea respetando saltos de línea en campos (RFC 4180).</li>
   * <li>Valida consistencia de columnas contra el header.</li>
   * <li>Acumula filas válidas en un buffer de memoria (~5MB).</li>
   * <li>Sube chunks a S3 en paralelo en cuanto el buffer se llena.</li>
   * <li>Consolida el archivo en S3 al finalizar.</li>
   * </ol>
   *
   * @param file Referencia al archivo físico (temporal).
   * @param fileId ID de trazabilidad.
   * @param key Ruta destino en S3.
   */
  private void processFileAsync(IngestionFile file, UUID fileId, String key) {
    String uploadId = null;
    Map<Integer, String> completedParts = new ConcurrentHashMap<>();
    var uploadTasks = new ArrayList<CompletableFuture<Void>>();

    long totalLines = 0;
    long skippedLines = 0;
    int expectedColumns = -1;


    try (
        InputStreamReader isr = new InputStreamReader(file.content(), StandardCharsets.UTF_8);
        CSVReader csvReader = new CSVReader(isr);

        ByteArrayOutputStream buffer = new ByteArrayOutputStream(MIN_PART_SIZE_BYTES + BUFFER_SAFETY_MARGIN);
        OutputStreamWriter bufferWriter = new OutputStreamWriter(buffer, StandardCharsets.UTF_8);
        CSVWriter csvWriter = new CSVWriter(bufferWriter) // <-- Para re-escribir CSV limpio al buffer
    ) {

      uploadId = storagePort.initMultipartUpload(key, file.contentType());
      IngestionLogEvent.MULTIPART_INITIATED.log(log, uploadId);

      int partNumber = 1;
      String[] record; // Esta variable se recicla en cada vuelta (safe for memory)

      // OpenCSV maneja saltos de línea dentro de comillas automáticamente
      while ((record = csvReader.readNext()) != null) {


        if (record.length == 0 || (record.length == 1 && record[0].isEmpty())) {
          continue;
        }

      // EL header, la primera linea sirve como modelo para el resto del archivo
        if (expectedColumns == -1) {
          expectedColumns = record.length;
          csvWriter.writeNext(record);
          totalLines++;
          continue;
        }


        if (record.length != expectedColumns) {
          skippedLines++;
          if (skippedLines <= 10) {
            IngestionLogEvent.VALIDATION_WARNING.log(log, expectedColumns, record.length, "Row content hidden");
          }
          continue;
        }


        csvWriter.writeNext(record);
        csvWriter.flush(); // Flusheamos para no sobrecargar memoria
        totalLines++;


        if (buffer.size() >= MIN_PART_SIZE_BYTES) {
          uploadChunk(key, uploadId, partNumber, buffer.toByteArray(), completedParts, uploadTasks);
          partNumber++;
          buffer.reset(); // Vaciamos el buffer
        }
      }

      // S3 Requieere un mínimo de 5mbs (Para evitar sobrecarga de metadatos, entre otras cosas)
      // Excepto el ultimo chunk, que puede ser de un tamaño arbitrario
      csvWriter.flush();
      if (buffer.size() > 0) {
        uploadChunk(key, uploadId, partNumber, buffer.toByteArray(), completedParts, uploadTasks);
      }

      // Futuros para gestionar los VTs
      CompletableFuture.allOf(uploadTasks.toArray(new CompletableFuture[0]))
          .orTimeout(UPLOAD_TIMEOUT_MINUTES, TimeUnit.MINUTES)
          .join();

      storagePort.completeMultipartUpload(key, uploadId, completedParts);

      if (skippedLines > 0) {
        IngestionLogEvent.INGESTION_COMPLETED_WITH_WARNINGS.log(log, totalLines, skippedLines);
      } else {
        IngestionLogEvent.MULTIPART_COMPLETED.log(log, completedParts.size());
      }

    } catch (Exception e) {
      log.error("Error crítico durante el procesamiento asíncrono", e);
      IngestionLogEvent.STORAGE_ERROR.log(log, fileId, e.getMessage());

      if (uploadId != null) {
        try {
          storagePort.abortMultipartUpload(key, uploadId);
          IngestionLogEvent.MULTIPART_ABORTED.log(log, fileId, "Rollback ejecutado");
        } catch (Exception ex) {
          log.error("Error al abortar multipart", ex);
        }
      }
    } finally {



      // Esto borra el archivo temporal en /tmp (El cierre del callback patter del controlador)
      if (file.cleanupCallback() != null) {
        file.cleanupCallback().run();
        log.debug("Cleanup callback ejecutado para archivo {}", fileId);
      }
    }
  }

  /**
   * Helper para subir un trozo usando el patrón "Bridge" para permitir cancelación real de hilos.
   */
  private void uploadChunk(String key, String uploadId, int partNum, byte[] payload,
      Map<Integer, String> partsMap,
      List<CompletableFuture<Void>> tasks) {


    CompletableFuture<Void> promise = new CompletableFuture<>();


    java.util.concurrent.Future<?> physicalTask = executor.submit(() -> {
      try {
        String eTag = storagePort.uploadPart(key, uploadId, partNum, payload);
        partsMap.put(partNum, eTag);
        IngestionLogEvent.PART_UPLOADED.log(log, partNum, eTag);
        promise.complete(null);
      } catch (Exception e) {
        promise.completeExceptionally(e);
      }
    });


    promise.whenComplete((result, error) -> {
      if (promise.isCancelled()) {
        physicalTask.cancel(true); // Esto corta la conexión TCP de S3
        log.debug("Interrupción enviada al hilo de la parte {}", partNum);
      }
    });

    tasks.add(promise);
  }

  // --- Métodos Auxiliares de Validación ---

  private void writeLineToBuffer(String line, ByteArrayOutputStream buffer) throws IOException {

    buffer.write((line + "\n").getBytes(StandardCharsets.UTF_8));
  }

  /**
   * Cuenta columnas CSV respetando comillas dobles.
   * Algoritmo de baja latencia (sin regex/split).
   */
  private int countCsvColumns(String line) {
    if (line == null || line.isEmpty()) return 0;
    int columns = 1;
    boolean inQuotes = false;
    for (int i = 0; i < line.length(); i++) {
      char c = line.charAt(i);
      if (c == '"') {
        inQuotes = !inQuotes;
      } else if (c == ',' && !inQuotes) {
        columns++;
      }
    }
    return columns;
  }

  private String truncate(String input, int maxLength) {
    if (input == null || input.length() <= maxLength) return input;
    return input.substring(0, maxLength) + "...";
  }
}