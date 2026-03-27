package com.refiq.platform.modules.ingestion;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.refiq.platform.ingestion.internal.domain.Analyte; // <-- Importante
import com.refiq.platform.ingestion.internal.domain.IngestionFile;
import com.refiq.platform.ingestion.internal.port.StoragePort;
import com.refiq.platform.ingestion.internal.service.IngestionService;
import java.io.ByteArrayInputStream;
import java.util.Map;
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

  @InjectMocks
  IngestionService ingestionService;

  @Test
  @DisplayName("Rollback Test: Should abort Multipart and cleanup when S3 upload fails")
  void should_AbortUpload_And_Cleanup_When_S3Fails() {
    // GIVEN
    String content = "Header\nData";
    AtomicBoolean cleanedUp = new AtomicBoolean(false);

    IngestionFile file = new IngestionFile(
        "test_fail.csv",
        Analyte.ALP, // <-- Uso del Enum
        () -> new ByteArrayInputStream(content.getBytes()),
        content.length(),
        "text/csv",
        () -> cleanedUp.set(true)
    );

    when(storagePort.initMultipartUpload(anyString(), anyString(), anyMap())).thenReturn("upload-123");

    when(storagePort.uploadPart(anyString(), eq("upload-123"), anyInt(), any()))
        .thenThrow(new RuntimeException("S3 Connection Timeout"));

    // WHEN
    ingestionService.ingest(file);

    // THEN
    await().atMost(2, SECONDS).untilAsserted(() -> {
      verify(storagePort).abortMultipartUpload(anyString(), eq("upload-123"));
      assertThat(cleanedUp.get()).isTrue();
    });
  }

  @Test
  @DisplayName("Happy Path: Should route raw bytes to specific analyte folder and inject metadata")
  void should_RouteBytesToAnalyteFolder_With_Metadata() {
    // GIVEN
    String csvContent = "Raw;Data;No;Mapping";
    AtomicBoolean cleanedUp = new AtomicBoolean(false);

    IngestionFile file = new IngestionFile(
        "dirty.csv",
        Analyte.CRE, // <-- Uso del Enum
        () -> new ByteArrayInputStream(csvContent.getBytes()),
        csvContent.length(),
        "text/csv",
        () -> cleanedUp.set(true)
    );

    ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<Map<String, String>> metadataCaptor = ArgumentCaptor.forClass(Map.class);

    when(storagePort.initMultipartUpload(keyCaptor.capture(), anyString(), metadataCaptor.capture()))
        .thenReturn("up-ok");

    when(storagePort.uploadPart(anyString(), eq("up-ok"), anyInt(), any())).thenReturn("etag-123");

    // WHEN
    ingestionService.ingest(file);

    // THEN
    await().atMost(2, SECONDS).untilAsserted(() -> {
      verify(storagePort).completeMultipartUpload(anyString(), eq("up-ok"), anyMap());
      assertThat(cleanedUp.get()).isTrue();

      // 1. Validar la nueva ruta de Sahel: raw/ANALYTE/ANALYTE_uuid.csv
      String usedKey = keyCaptor.getValue();
      assertThat(usedKey)
          .startsWith("raw/CRE/CRE_")
          .endsWith(".csv");

      // 2. Verificamos que los metadatos requeridos por Data Science están presentes
      Map<String, String> usedMetadata = metadataCaptor.getValue();
      assertThat(usedMetadata)
          .containsEntry("original-filename", "dirty.csv")
          .containsEntry("analyte", "CRE")
          .containsKey("record-id");

      // 3. Verificamos que los bytes enviados son EXACTAMENTE los recibidos
      ArgumentCaptor<byte[]> payloadCaptor = ArgumentCaptor.forClass(byte[].class);
      verify(storagePort, atLeastOnce()).uploadPart(anyString(), eq("up-ok"), anyInt(), payloadCaptor.capture());

      String uploadedContent = new String(payloadCaptor.getValue());
      assertThat(uploadedContent).isEqualTo(csvContent);
    });
  }

  @Test
  @DisplayName("Async Coordination: Should await completion of all asynchronous chunk uploads")
  void should_Wait_For_Async_Tasks_To_Complete() {
    // GIVEN
    String csvContent = "H1;H2\nD1;D2";
    IngestionFile file = new IngestionFile(
        "async.csv",
        Analyte.FT4, // <-- Uso del Enum
        () -> new ByteArrayInputStream(csvContent.getBytes()),
        10,
        "text/csv",
        () -> {}
    );

    when(storagePort.initMultipartUpload(anyString(), anyString(), anyMap())).thenReturn("up-async");

    // SIMULAMOS LATENCIA EN RED PARA S3
    when(storagePort.uploadPart(anyString(), anyString(), anyInt(), any())).thenAnswer(invocation -> {
      Thread.sleep(100);
      return "etag-delayed";
    });

    // WHEN
    ingestionService.ingest(file);

    // THEN
    await().atMost(2, SECONDS).untilAsserted(() -> {
      verify(storagePort).completeMultipartUpload(anyString(), eq("up-async"), anyMap());
    });
  }
}