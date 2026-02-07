package com.refiq.platform.modules.ingestion;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


import com.refiq.platform.ingestion.internal.domain.IngestionFile;
import com.refiq.platform.ingestion.internal.normalizer.CanonicalCSV;
import com.refiq.platform.ingestion.internal.normalizer.CsvNormalizer;
import com.refiq.platform.ingestion.internal.normalizer.NormalizationResult;
import com.refiq.platform.ingestion.internal.port.StoragePort;
import com.refiq.platform.ingestion.internal.service.IngestionService;
import java.io.ByteArrayInputStream;
import java.util.concurrent.atomic.AtomicBoolean; // Para verificar el callback sin Mockito
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
  void should_AbortUpload_And_Cleanup_When_CanonicalUploadFails() {
    // GIVEN
    String content = "Header\nData";

    // FIX JDK 26: Usamos AtomicBoolean en lugar de mock(Runnable.class)
    AtomicBoolean cleanedUp = new AtomicBoolean(false);
    Runnable realCleanup = () -> cleanedUp.set(true);

    IngestionFile file = new IngestionFile(
        "test_fail.csv",
        () -> new ByteArrayInputStream(content.getBytes()),
        content.length(),
        "text/csv",
        realCleanup
    );


    when(storagePort.upload(eq(file), anyString())).thenReturn("raw/path");


    when(storagePort.initMultipartUpload(any(), any())).thenReturn("upload-123");


    when(storagePort.uploadPart(any(), eq("upload-123"), anyInt(), any()))
        .thenThrow(new RuntimeException("S3 Caído durante Canonical"));


    when(normalizer.normalize(any())).thenReturn(
        new NormalizationResult.Success(new CanonicalCSV("1", "10", "2000-01-01", "M", "10.5", "unit", "hash"))
    );

    // WHEN
    ingestionService.ingest(file);

    // THEN
    await().atMost(2, SECONDS).untilAsserted(() -> {
      assertThat(cleanedUp.get()).as("El cleanup callback debe ejecutarse siempre").isTrue();
    });


    verify(storagePort).abortMultipartUpload(anyString(), eq("upload-123"));
  }

  @Test
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
      // Verifica que se completó la subida canónica
      verify(storagePort).completeMultipartUpload(any(), eq("up-clean"), any());
      assertThat(cleanedUp.get()).isTrue();
    });

    // Validamos que lo que se subió tiene formato CSV estándar (con comas)
    ArgumentCaptor<byte[]> payloadCaptor = ArgumentCaptor.forClass(byte[].class);
    verify(storagePort).uploadPart(any(), eq("up-clean"), anyInt(), payloadCaptor.capture());

    String uploadedContent = new String(payloadCaptor.getValue());

    // CanonicalCSV usa comas: "1","100","2020-01-01","M","10.5","N/A","hash"
    assertThat(uploadedContent).contains("\"1\",\"100\"");
    assertThat(uploadedContent).contains("\"10.5\""); // Punto decimal
  }
}