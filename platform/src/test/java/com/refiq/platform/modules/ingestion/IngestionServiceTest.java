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

import com.refiq.platform.ingestion.api.event.FileIngestedEvent;
import com.refiq.platform.ingestion.internal.domain.Analyte;
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
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class IngestionServiceTest {

  @Mock
  StoragePort storagePort;

  @Mock
  ApplicationEventPublisher eventPublisher;

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
        Analyte.ALP,
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
    // Verificamos que el evento de Modulith se disparó (ocurre sincrónicamente antes del fallo de red)
    verify(eventPublisher).publishEvent(any(FileIngestedEvent.class));

    await().atMost(2, SECONDS).untilAsserted(() -> {
      verify(storagePort).abortMultipartUpload(anyString(), eq("upload-123"));
      assertThat(cleanedUp.get()).isTrue();
    });
  }

  @Test
  @DisplayName("Happy Path: Should route raw bytes, inject metadata and publish Spring Event")
  void should_RouteBytesToAnalyteFolder_With_Metadata_And_PublishEvent() {
    // GIVEN
    String csvContent = "Raw;Data;No;Mapping";
    AtomicBoolean cleanedUp = new AtomicBoolean(false);

    IngestionFile file = new IngestionFile(
        "dirty.csv",
        Analyte.CRE,
        () -> new ByteArrayInputStream(csvContent.getBytes()),
        csvContent.length(),
        "text/csv",
        () -> cleanedUp.set(true)
    );

    ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<Map<String, String>> metadataCaptor = ArgumentCaptor.forClass(Map.class);
    ArgumentCaptor<FileIngestedEvent> eventCaptor = ArgumentCaptor.forClass(FileIngestedEvent.class);

    when(storagePort.initMultipartUpload(keyCaptor.capture(), anyString(), metadataCaptor.capture()))
        .thenReturn("up-ok");

    when(storagePort.uploadPart(anyString(), eq("up-ok"), anyInt(), any())).thenReturn("etag-123");

    // WHEN
    ingestionService.ingest(file);

    // THEN
    // 1. Verificamos la publicación del evento a Modulith y capturamos el UUID generado
    verify(eventPublisher).publishEvent(eventCaptor.capture());
    FileIngestedEvent publishedEvent = eventCaptor.getValue();
    assertThat(publishedEvent.testCode()).isEqualTo("CRE");
    assertThat(publishedEvent.fileId()).isNotNull();

    // 2. Verificamos el flujo asíncrono y la consistencia del UUID
    await().atMost(2, SECONDS).untilAsserted(() -> {
      verify(storagePort).completeMultipartUpload(anyString(), eq("up-ok"), anyMap());
      assertThat(cleanedUp.get()).isTrue();

      String usedKey = keyCaptor.getValue();
      assertThat(usedKey)
          .startsWith("1.Bronze/CRE/CRE_")
          .endsWith(".csv")
          .contains(publishedEvent.fileId().toString()); // El nombre del archivo debe tener el UUID del evento

      Map<String, String> usedMetadata = metadataCaptor.getValue();
      assertThat(usedMetadata)
          .containsEntry("original-filename", "dirty.csv")
          .containsEntry("analyte", "CRE")
          .containsEntry("record-id", publishedEvent.fileId().toString()); // La metadata debe tener el mismo UUID

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
        Analyte.FT4,
        () -> new ByteArrayInputStream(csvContent.getBytes()),
        10,
        "text/csv",
        () -> {}
    );

    when(storagePort.initMultipartUpload(anyString(), anyString(), anyMap())).thenReturn("up-async");

    when(storagePort.uploadPart(anyString(), anyString(), anyInt(), any())).thenAnswer(invocation -> {
      Thread.sleep(100);
      return "etag-delayed";
    });

    // WHEN
    ingestionService.ingest(file);

    // THEN
    verify(eventPublisher).publishEvent(any(FileIngestedEvent.class));

    await().atMost(2, SECONDS).untilAsserted(() -> {
      verify(storagePort).completeMultipartUpload(anyString(), eq("up-async"), anyMap());
    });
  }
} 