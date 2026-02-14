package com.refiq.platform.modules.ingestion;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.refiq.platform.ingestion.internal.domain.IngestionFile;
import com.refiq.platform.ingestion.internal.normalizer.CanonicalCSV;
import com.refiq.platform.ingestion.internal.normalizer.CsvNormalizer;
import com.refiq.platform.ingestion.internal.normalizer.NormalizationResult;
import com.refiq.platform.ingestion.internal.port.StoragePort;
import com.refiq.platform.ingestion.internal.service.IngestionService;
import java.io.ByteArrayInputStream;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IngestionServiceTest {

  @Mock
  StoragePort storagePort;

  @Mock
  CsvNormalizer normalizer;

  @InjectMocks
  IngestionService ingestionService;

  @Test
  @DisplayName("Rollback Test: Should abort Multipart Upload and cleanup resources on partial failure")
  void should_AbortUpload_And_Cleanup_When_CanonicalUploadFails() {
    // GIVEN
    String content = "Header\nData";
    AtomicBoolean cleanedUp = new AtomicBoolean(false);

    IngestionFile file = new IngestionFile(
        "test_fail.csv",
        () -> new ByteArrayInputStream(content.getBytes()),
        content.length(),
        "text/csv",
        () -> cleanedUp.set(true)
    );

    when(storagePort.upload(eq(file), anyString())).thenReturn("raw/path");
    when(storagePort.initMultipartUpload(any(), any())).thenReturn("upload-123");

    // Simulamos fallo en la subida de una parte
    // Nota: Como corre dentro de un CompletableFuture, la excepción será capturada
    // y propagada en el .join() del servicio.
    when(storagePort.uploadPart(any(), eq("upload-123"), anyInt(), any()))
        .thenThrow(new RuntimeException("S3 Caído durante Canonical"));

    // Mock del normalizador para que devuelva algo y el bucle avance
    when(normalizer.normalize(any())).thenReturn(
        new NormalizationResult.Success(new CanonicalCSV("1", "10", "2000-01-01", "M", "10.5", "unit", "hash"))
    );

    // WHEN
    ingestionService.ingest(file);

    // THEN

    await().atMost(2, SECONDS).untilAsserted(() -> {


      verify(storagePort).abortMultipartUpload(anyString(), eq("upload-123"));


      assertThat(cleanedUp.get())
          .as("El cleanup callback debe ejecutarse siempre, incluso en fallo")
          .isTrue();
    });
  }

  @Test
  @DisplayName("Happy Path: Should normalize, upload parts, and complete Multipart transaction")
  void should_Normalize_And_UploadCanonical() {
    // GIVEN

    String csvContent = """
        ID;Age;DOB;Sex;Res;Val
        1;100;2020-01-01;M;10;10,5
        """;

    AtomicBoolean cleanedUp = new AtomicBoolean(false);

    IngestionFile file = new IngestionFile(
        "dirty.csv",
        () -> new ByteArrayInputStream(csvContent.getBytes()),
        csvContent.length(),
        "text/csv",
        () -> cleanedUp.set(true)
    );

    when(storagePort.upload(eq(file), anyString())).thenReturn("raw/ok");
    when(storagePort.initMultipartUpload(any(), any())).thenReturn("up-clean");


    when(storagePort.uploadPart(any(), eq("up-clean"), anyInt(), any())).thenReturn("etag-ok");

    when(normalizer.normalize(any())).thenReturn(
        new NormalizationResult.Success(
            new CanonicalCSV("1", "100", "2020-01-01", "M", "10.5", "N/A", "hash")
        )
    );

    // WHEN
    ingestionService.ingest(file);

    // THEN
    await().atMost(2, SECONDS).untilAsserted(() -> {

      verify(storagePort).completeMultipartUpload(any(), eq("up-clean"), any());
      assertThat(cleanedUp.get()).isTrue();


      ArgumentCaptor<byte[]> payloadCaptor = ArgumentCaptor.forClass(byte[].class);
      verify(storagePort, atLeastOnce()).uploadPart(any(), eq("up-clean"), anyInt(), payloadCaptor.capture());

      String uploadedContent = new String(payloadCaptor.getValue());

      // Validamos formato canónico (Comillas y comas, generado por CSVWriter)
      // "loinc_code","age_days","date_of_birth","sex","value","unit","row_hash"
      assertThat(uploadedContent).contains("\"1\",\"100\"");
      assertThat(uploadedContent).contains("\"10.5\""); // Punto decimal normalizado
    });
  }

  @Test
  @DisplayName("Async Coordination: Should await completion of all asynchronous tasks")
  void should_Wait_For_Async_Tasks_To_Complete() {
    // GIVEN


    String csvContent = "H1;H2\nD1;D2";
    IngestionFile file = new IngestionFile("async.csv", () -> new ByteArrayInputStream(csvContent.getBytes()), 10, "text/csv", () -> {});

    when(storagePort.initMultipartUpload(any(), any())).thenReturn("up-async");
    when(normalizer.normalize(any())).thenReturn(new NormalizationResult.Ignored()); // Simplificamos

    // SIMULAMOS LATENCIA EN S3

    when(storagePort.uploadPart(any(), any(), anyInt(), any())).thenAnswer(invocation -> {
      Thread.sleep(100); // Latencia artificial
      return "etag-delayed";
    });

    // WHEN
    ingestionService.ingest(file);

    // THEN
    await().atMost(2, SECONDS).untilAsserted(() -> {
      verify(storagePort).completeMultipartUpload(any(), eq("up-async"), any());
    });
  }
}