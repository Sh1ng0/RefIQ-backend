package com.refiq.platform.modules.ingestion;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencsv.CSVWriter;
import com.refiq.platform.ingestion.internal.domain.IngestionFile;
import com.refiq.platform.ingestion.internal.port.StoragePort;
import com.refiq.platform.ingestion.internal.service.IngestionService;
import java.io.ByteArrayInputStream;
import java.io.StringWriter;
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
  void should_AbortUpload_And_Cleanup_When_OnePartFails() {
    // GIVEN
    String hugeLine = "a".repeat(1024 * 1024); // 1MB linea
    StringBuilder sb = new StringBuilder();
    // Big file para ver el chunking en acción
    for(int i=0; i<15; i++) {
      sb.append(hugeLine).append("\n");
    }
    String content = sb.toString();


    Runnable cleanupMock = mock(Runnable.class);

    IngestionFile file = new IngestionFile(
        "test_fail.csv",
        new ByteArrayInputStream(content.getBytes()),
        content.length(),
        "text/csv",
        cleanupMock
    );

    when(storagePort.initMultipartUpload(any(), any())).thenReturn("upload-123");
    when(storagePort.uploadPart(any(), eq("upload-123"), eq(1), any())).thenReturn("etag-1");


    when(storagePort.uploadPart(any(), eq("upload-123"), eq(2), any()))
        .thenThrow(new RuntimeException("S3 Caído"));

    //  WHEN
    ingestionService.ingest(file);


    await().atMost(2, SECONDS).untilAsserted(() -> {
      verify(cleanupMock, times(1)).run();
    });

    // THEN
    verify(storagePort).abortMultipartUpload(anyString(), eq("upload-123"));
    verify(storagePort, never()).completeMultipartUpload(any(), any(), any());
  }

  @Test
  void should_FilterInvalidLines_And_UploadOnlyCleanContent() {
    // GIVEN
    String csvContent = """
        Col1,Col2,Col3
        Val1,Val2,Val3
        Val1,Val2
        ValX,ValY,ValZ
        ERROR
        Final,Line,OK
        """;

    Runnable cleanupMock = mock(Runnable.class);

    IngestionFile file = new IngestionFile(
        "dirty.csv",
        new ByteArrayInputStream(csvContent.getBytes()),
        csvContent.length(),
        "text/csv",
        cleanupMock
    );

    when(storagePort.initMultipartUpload(any(), any())).thenReturn("up-clean");
    when(storagePort.uploadPart(any(), eq("up-clean"), anyInt(), any())).thenReturn("etag-ok");

    //  WHEN
    ingestionService.ingest(file);

    //  THEN
    await().atMost(2, SECONDS).untilAsserted(() -> {
      verify(storagePort).completeMultipartUpload(any(), eq("up-clean"), any());
      verify(cleanupMock).run(); // El archivo temporal debe morir
    });


    ArgumentCaptor<byte[]> payloadCaptor = ArgumentCaptor.forClass(byte[].class);
    verify(storagePort).uploadPart(any(), eq("up-clean"), anyInt(), payloadCaptor.capture());

    String uploadedContent = new String(payloadCaptor.getValue());

    // Nota: CSVWriter por defecto pone comillas dobles: "Col1","Col2","Col3"

    assertThat(uploadedContent).contains("\"Col1\",\"Col2\",\"Col3\"");
    assertThat(uploadedContent).contains("\"Val1\",\"Val2\",\"Val3\"");


    assertThat(uploadedContent).doesNotContain("ERROR");

    assertThat(uploadedContent).doesNotContain("\"Val1\",\"Val2\"\n");
  }

  @Test
  void should_Handle_Newlines_Inside_Quotes() {
    // GIVEN (El caso por el que usamos OpenCSV)

    String difficultCsv = """
        ID,Description,Price
        1,"Producto con
        salto de linea",100
        """;

    Runnable cleanupMock = mock(Runnable.class);
    IngestionFile file = new IngestionFile("complex.csv",
        new ByteArrayInputStream(difficultCsv.getBytes()), difficultCsv.length(), "text/csv", cleanupMock);

    when(storagePort.initMultipartUpload(any(), any())).thenReturn("up-complex");
    when(storagePort.uploadPart(any(), any(), anyInt(), any())).thenReturn("etag");

    //  WHEN
    ingestionService.ingest(file);

    //  THEN
    await().atMost(2, SECONDS).untilAsserted(() -> {
      verify(storagePort).completeMultipartUpload(any(), any(), any());
    });

    ArgumentCaptor<byte[]> payloadCaptor = ArgumentCaptor.forClass(byte[].class);
    verify(storagePort).uploadPart(any(), any(), anyInt(), payloadCaptor.capture());
    String uploaded = new String(payloadCaptor.getValue());


    assertThat(uploaded).contains("\"1\"");
    assertThat(uploaded).contains("100");
    // CSVWriter normalizará el salto de línea dentro de las comillas
    assertThat(uploaded).contains("Producto con\nsalto de linea");
  }
}