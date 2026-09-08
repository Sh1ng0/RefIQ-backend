package com.refiq.platform.ingestion.internal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.refiq.platform.ingestion.api.dto.IngestionResult;
import com.refiq.platform.ingestion.api.dto.Status;
import com.refiq.platform.ingestion.api.event.FileAcceptedEvent;
import com.refiq.platform.ingestion.internal.domain.Analyte;
import com.refiq.platform.ingestion.internal.domain.IngestionFile;
import com.refiq.platform.support.slices.BaseS3Test;
import com.refiq.platform.support.slices.RefiqModuleTest;
import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.modulith.test.PublishedEvents;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;

@RefiqModuleTest
@DisplayName("Ingestion - Service & S3 Storage (LocalStack Integration)")
class IngestionServiceIntegrationTest extends BaseS3Test {

  @Autowired
  private IngestionService ingestionService;

  @Autowired
  private S3Client s3Client;

  @Value("${refiq.storage.s3.bucket-name}")
  private String bucketName;

  @Test
  @DisplayName("Should process a file > 5MB, upload it via multipart to S3, and publish FileAcceptedEvent")
  void shouldUploadLargeFileMultipartAndPublishEvent(PublishedEvents events) {
    // GIVEN
    int size6MB = 6 * 1024 * 1024;
    byte[] fakeData = new byte[size6MB];

    for (int i = 0; i < fakeData.length; i++) {
      fakeData[i] = (byte) (i % 256);
    }

    IngestionFile file = new IngestionFile(
        "patients_gold.csv",
        Analyte.TSH,
        () -> new ByteArrayInputStream(fakeData),
        size6MB,
        "text/csv",
        () -> {}
    );

    // WHEN
    IngestionResult result = ingestionService.ingest(file);

    // THEN Sync
    assertThat(result).isInstanceOf(IngestionResult.Success.class);
    IngestionResult.Success success = (IngestionResult.Success) result;
    assertThat(success.response().status()).isEqualTo(Status.PENDING_PROCESSING);

    UUID fileId = success.response().fileId();
    String expectedS3Key = "1.Bronze/TSH/TSH_" + fileId + ".csv";

    // THEN Async
    await()
        .atMost(Duration.ofSeconds(15))
        .pollInterval(Duration.ofSeconds(1))
        .untilAsserted(() -> {
          HeadObjectResponse s3Object = s3Client.headObject(HeadObjectRequest.builder()
              .bucket(bucketName)
              .key(expectedS3Key)
              .build());

          assertThat(s3Object.contentLength()).isEqualTo(size6MB);
          assertThat(s3Object.metadata()).containsEntry("record-id", fileId.toString());
        });

    var publishedEvents = events.ofType(FileAcceptedEvent.class);
    assertThat(publishedEvents).hasSize(1);
    assertThat(publishedEvents.iterator().next().fileId()).isEqualTo(fileId);
  }
}