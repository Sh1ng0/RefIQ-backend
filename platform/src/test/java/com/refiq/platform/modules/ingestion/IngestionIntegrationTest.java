package com.refiq.platform.modules.ingestion;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.time.Duration;
import java.util.List;

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

  // Debe coincidir con el application.properties o el override del AbstractIntegrationTest
  private static final String BUCKET_NAME = "refiq-clinical-data-dev";

  @Test
  @DisplayName("Small File Ingestion: Should upload RAW and generate normalized CANONICAL")
  void shouldUploadSmallFileSuccessfully() throws Exception {
    // 1. ARRANGE
    // IMPORTANTE: Usamos el formato que espera CsvNormalizer (6 columnas, separador ;)
    // ID;Age;DOB;Sex;OriginalVal;ResultVal
    String content = "HEADER;IGNORED;ETC;ETC;ETC;ETC\n" +
        "LOINC-1;30;1990-01-01;Hombre;Ignored;10,5";

    MockMultipartFile file = new MockMultipartFile(
        "file", "small_test.csv", MediaType.TEXT_PLAIN_VALUE, content.getBytes()
    );

    // 2. ACT
    mockMvc.perform(multipart("/api/ingestion/upload").file(file))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.status").value("PENDING_PROCESSING"))
        .andExpect(jsonPath("$.fileId").isNotEmpty());

    // 3. ASSERT (Async)
    await().atMost(Duration.ofSeconds(5))
        .pollInterval(Duration.ofMillis(200))
        .untilAsserted(() -> {
          var response = s3Client.listObjects(b -> b.bucket(BUCKET_NAME));
          List<S3Object> files = response.contents();


          assertThat(files).hasSizeGreaterThanOrEqualTo(2);

          // 3a. Validar RAW (Debe contener el nombre original)
          S3Object rawFile = files.stream()
              .filter(o -> o.key().startsWith("raw/") && o.key().endsWith("small_test.csv"))
              .findFirst()
              .orElseThrow(() -> new AssertionError("No se encontró el archivo RAW en S3"));

          assertThat(rawFile.size()).isGreaterThan(0);

          // 3b. Validar CANONICAL (Debe estar en carpeta canonical/)
          S3Object canonicalFile = files.stream()
              .filter(o -> o.key().startsWith("canonical/"))
              .findFirst()
              .orElseThrow(() -> new AssertionError("No se encontró el archivo CANONICAL en S3"));

          // 3c. Validar CONTENIDO NORMALIZADO

          ResponseBytes<GetObjectResponse> objectBytes = s3Client.getObjectAsBytes(b -> b.bucket(BUCKET_NAME).key(canonicalFile.key()));
          String canonicalContent = objectBytes.asUtf8String();


          assertThat(canonicalContent).contains("10.5");
          assertThat(canonicalContent).contains("M");
          // Headers canónicos
          assertThat(canonicalContent).contains("loinc_code", "value", "sex");
        });
  }

  @Test
  @DisplayName("Large File Processing: Should handle Multipart Upload with Semaphore backpressure")
  void shouldProcessLargeFileInChunksSuccessfully() throws Exception {
    // 1. ARRANGE

    String header = "ID;Age;DOB;Sex;Res;Val\n";
    String rowData = "LOINC-TEST;45;1980-01-01;F;Ignored;123,45";

    String padding = "X".repeat(1000);


    String row = rowData + padding + "\n";


    StringBuilder largeCsv = new StringBuilder(header);
    for (int i = 0; i < 6000; i++) {
      largeCsv.append(rowData).append(";").append(padding).append("\n"); // Añadimos padding como columna extra (el normalizador la ignora si hay >6)
    }

    byte[] originalBytes = largeCsv.toString().getBytes();
    assertThat(originalBytes.length).isGreaterThan(5 * 1024 * 1024);

    MockMultipartFile file = new MockMultipartFile(
        "file", "large_test.csv", MediaType.TEXT_PLAIN_VALUE, originalBytes
    );

    // 2. ACT
    mockMvc.perform(multipart("/api/ingestion/upload").file(file))
        .andExpect(status().isAccepted());

    // 3. ASSERT
    await().atMost(Duration.ofSeconds(15))
        .pollInterval(Duration.ofMillis(500))
        .untilAsserted(() -> {
          var response = s3Client.listObjects(b -> b.bucket(BUCKET_NAME));

          // Verificar RAW
          var rawOpt = response.contents().stream()
              .filter(o -> o.key().startsWith("raw/") && o.key().endsWith("large_test.csv"))
              .findFirst();
          assertThat(rawOpt).isPresent();
          assertThat(rawOpt.get().size()).isGreaterThan(5 * 1024 * 1024);

          // Verificar CANONICAL
          // Si el archivo canónico existe y tiene tamaño considerable, significa que:
          // 1. El parser leyó el archivo grande.
          // 2. El semáforo permitió las subidas.
          // 3. El Multipart se completó.
          var canonicalOpt = response.contents().stream()
              .filter(o -> o.key().startsWith("canonical/"))
              .findFirst();

          assertThat(canonicalOpt).isPresent();
          assertThat(canonicalOpt.get().size()).isGreaterThan(100 * 1024);
        });
  }
}