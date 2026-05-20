package com.refiq.platform.modules.ingestion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.refiq.platform.auth.internal.repository.CredentialRepository;
import com.refiq.platform.ingestion.api.web.IngestionController;
import com.refiq.platform.ingestion.internal.adapter.s3.S3StorageAdapter;
import com.refiq.platform.ingestion.internal.service.IngestionService;
import com.refiq.platform.shared.config.S3Config;
import com.refiq.platform.user.internal.repository.UserProfileRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.DispatcherServletAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.ServletWebServerFactoryAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import com.fasterxml.jackson.databind.JsonNode;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = {
    IngestionController.class,
    IngestionService.class,
    S3Config.class
})
@Import({
    JacksonAutoConfiguration.class,
    ServletWebServerFactoryAutoConfiguration.class,
    DispatcherServletAutoConfiguration.class,
    WebMvcAutoConfiguration.class,
    S3StorageAdapter.class,
    HttpMessageConvertersAutoConfiguration.class
})
@ActiveProfiles("test")
@AutoConfigureMockMvc(addFilters = false)
class IngestionIntegrationTest extends AbstractIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private S3Client s3Client;

  @Autowired
  private ObjectMapper objectMapper;

  @MockitoBean
  private com.refiq.platform.user.internal.repository.UserProfileRepository userProfileRepository;

  @MockitoBean
  private com.refiq.platform.auth.internal.repository.CredentialRepository credentialRepository;

  private static final String BUCKET_NAME = "refiq-clinical-data-dev";

  @Test
  @DisplayName("Integration: Should accept valid analyte and upload raw CSV directly to its specific folder")
  void shouldUploadSmallFileSuccessfully() throws Exception {

    String analyteStr = "ALP"; // Tiene que coincidir con el Enum Analyte.ALP
    String content = "HEADER;IGNORED;ETC\nVAL1;VAL2;VAL3";

    MockMultipartFile file = new MockMultipartFile(
        "file", "small_test.csv", MediaType.TEXT_PLAIN_VALUE, content.getBytes()
    );

    // 2. ACT
    mockMvc.perform(multipart("/api/ingestion/upload")
            .file(file)
            .param("analyte", analyteStr))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.status").value("PENDING_PROCESSING"))
        .andExpect(jsonPath("$.fileId").isNotEmpty());

    // 3. ASSERT (Async)
    await().atMost(Duration.ofSeconds(5))
        .pollInterval(Duration.ofMillis(200))
        .untilAsserted(() -> {
          var response = s3Client.listObjects(b -> b.bucket(BUCKET_NAME));


          S3Object rawFile = response.contents().stream()
              .filter(o -> o.key().startsWith("raw/" + analyteStr + "/"))
              .findFirst()
              .orElseThrow(() -> new AssertionError("No se encontró el archivo RAW en la carpeta del analito"));


          ResponseBytes<GetObjectResponse> objectBytes = s3Client.getObjectAsBytes(b -> b.bucket(BUCKET_NAME).key(rawFile.key()));
          String downloadedContent = objectBytes.asUtf8String();

          assertThat(downloadedContent).isEqualTo(content);
        });
  }

  @Test
  @DisplayName("Integration: Should handle Large File Upload to specific folder with backpressure")
  void shouldProcessLargeFileInChunksSuccessfully() throws Exception {
    // 1. ARRANGE
    String analyteStr = "CRE";
    String header = "ID;Age;DOB;Sex;Res;Val\n";
    String padding = "X".repeat(1024);
    String heavyRow = "LOINC-TEST;45;1980-01-01;F;Ignored" + padding + ";123,45\n";

    StringBuilder largeCsv = new StringBuilder(header);
    for (int i = 0; i < 6000; i++) {
      largeCsv.append(heavyRow);
    }

    byte[] originalBytes = largeCsv.toString().getBytes();
    MockMultipartFile file = new MockMultipartFile(
        "file", "large_test.csv", MediaType.TEXT_PLAIN_VALUE, originalBytes
    );


    MvcResult result = mockMvc.perform(multipart("/api/ingestion/upload")
            .file(file)
            .param("analyte", analyteStr))
        .andExpect(status().isAccepted())
        .andReturn();


    String jsonResponse = result.getResponse().getContentAsString();
    JsonNode jsonNode = objectMapper.readTree(jsonResponse);
    String fileId = jsonNode.get("fileId").asText();


    String expectedKey = "raw/" + analyteStr + "/" + analyteStr + "_" + fileId + ".csv";

    await().atMost(Duration.ofSeconds(15))
        .pollInterval(Duration.ofMillis(500))
        .untilAsserted(() -> {
          var response = s3Client.listObjects(b -> b.bucket(BUCKET_NAME));


          var uploadedFile = response.contents().stream()
              .filter(o -> o.key().equals(expectedKey))
              .findFirst();

          assertThat(uploadedFile).isPresent();
          assertThat(uploadedFile.get().size()).isEqualTo(originalBytes.length);
        });
  }

  @Test
  @DisplayName("Integration: Should return 400 Bad Request if analyte is not in the Enum")
  void shouldRejectInvalidAnalyte() throws Exception {
    // 1. ARRANGE
    String invalidAnalyte = "INVENTADO";
    MockMultipartFile file = new MockMultipartFile(
        "file", "test.csv", MediaType.TEXT_PLAIN_VALUE, "dummy content".getBytes()
    );

    // 2. ACT & ASSERT
    mockMvc.perform(multipart("/api/ingestion/upload")
            .file(file)
            .param("analyte", invalidAnalyte))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("Analito no soportado: " + invalidAnalyte));
  }
}