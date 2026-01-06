package com.refiq.platform.modules.ingestion;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc(addFilters = false)
class IngestionIntegrationTest extends AbstractIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private S3Client s3Client;

  private static final String BUCKET_NAME = "refiq-clinical-data-dev";

  @Test
  void shouldUploadSmallFileSuccessfully() throws Exception {
    // 1. ARRANGE
    String content = "patient_id,diagnosis,date\n123,J00,2023-01-01";
    MockMultipartFile file = new MockMultipartFile(
        "file", "small_test.csv", MediaType.TEXT_PLAIN_VALUE, content.getBytes()
    );

    // 2. ACT
    mockMvc.perform(multipart("/api/ingestion/upload").file(file))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.status").value("PENDING_PROCESSING"))
        .andExpect(jsonPath("$.fileId").isNotEmpty());

    // 3. ASSERT
    await().atMost(Duration.ofSeconds(5))
        .pollInterval(Duration.ofMillis(100))
        .untilAsserted(() -> {
          var response = s3Client.listObjects(b -> b.bucket(BUCKET_NAME));

          assertThat(response.contents())
              .as("El bucket debería contener el archivo subido")
              .hasSize(1);

          String key = response.contents().getFirst().key();
          assertThat(key).endsWith("-small_test.csv");

          ResponseBytes<GetObjectResponse> objectBytes = s3Client.getObjectAsBytes(b -> b.bucket(BUCKET_NAME).key(key));
          String s3Content = objectBytes.asUtf8String();


          assertThat(s3Content).contains("123");
          assertThat(s3Content).contains("J00");
        });
  }

  @Test
  void shouldProcessLargeFileInChunksSuccessfully() throws Exception {
    //  ARRANGE
    String header = "id,data\n";

    String rowData = "x".repeat(1024);
    String row = "123," + rowData + "\n";

    StringBuilder largeCsv = new StringBuilder(header);
    for (int i = 0; i < 6000; i++) {
      largeCsv.append(row);
    }

    byte[] originalBytes = largeCsv.toString().getBytes();


    assertThat(originalBytes.length).isGreaterThan(5 * 1024 * 1024);

    MockMultipartFile file = new MockMultipartFile(
        "file", "large_test.csv", MediaType.TEXT_PLAIN_VALUE, originalBytes
    );

    // ACT
    mockMvc.perform(multipart("/api/ingestion/upload").file(file))
        .andExpect(status().isAccepted());

    // ASSERT (Async)
    await().atMost(Duration.ofSeconds(10))
        .pollInterval(Duration.ofMillis(500))
        .untilAsserted(() -> {

          var response = s3Client.listObjects(b -> b.bucket(BUCKET_NAME));

          var s3ObjectOpt = response.contents().stream()
              .filter(o -> o.key().endsWith("-large_test.csv"))
              .findFirst();

          assertThat(s3ObjectOpt).isPresent();


          long s3Size = s3ObjectOpt.get().size();
          assertThat(s3Size)
              .as("El archivo en S3 debe ser mayor a 5MB para confirmar carga multipart")
              .isGreaterThan(5 * 1024 * 1024);

          assertThat(s3Size).isGreaterThanOrEqualTo(originalBytes.length);
        });
  }
}