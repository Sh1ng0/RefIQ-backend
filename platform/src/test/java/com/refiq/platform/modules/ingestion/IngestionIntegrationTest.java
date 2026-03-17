package com.refiq.platform.modules.ingestion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.refiq.platform.RefIqPlatformApplication;
import com.refiq.platform.auth.internal.security.JwtAuthenticationFilter;
import com.refiq.platform.auth.internal.security.JwtProvider;
import com.refiq.platform.support.security.TestSecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import com.fasterxml.jackson.databind.JsonNode;
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

//@AutoConfigureMockMvc(addFilters = false)
//@Import(TestSecurityConfig.class)
@AutoConfigureMockMvc
class IngestionIntegrationTest extends AbstractIntegrationTest {


  @Autowired
  private MockMvc mockMvc;


  @Autowired
  private S3Client s3Client;

  @Autowired
  private ObjectMapper objectMapper;

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
    // 1. ARRANGE (Tu construcción del CSV estaba perfecta)
    String header = "ID;Age;DOB;Sex;Res;Val\n";
    String padding = "X".repeat(1024);
    // Estructura: LOINC; Age; DOB; Sex; PADDING; Value (6 columnas)
    String heavyRow = "LOINC-TEST;45;1980-01-01;F;Ignored" + padding + ";123,45\n";

    StringBuilder largeCsv = new StringBuilder(header);
    for (int i = 0; i < 6000; i++) {
      largeCsv.append(heavyRow);
    }

    byte[] originalBytes = largeCsv.toString().getBytes();
    MockMultipartFile file = new MockMultipartFile(
        "file", "large_test.csv", MediaType.TEXT_PLAIN_VALUE, originalBytes
    );

    // 2. ACT: Capturamos la respuesta para obtener el ID
    MvcResult result = mockMvc.perform(multipart("/api/ingestion/upload").file(file))
        .andExpect(status().isAccepted())
        .andReturn();

    // Extraemos el fileId del JSON de respuesta
    String jsonResponse = result.getResponse().getContentAsString();
    JsonNode jsonNode = objectMapper.readTree(jsonResponse);
    String fileId = jsonNode.get("fileId").asText();

    // 3. ASSERT: Buscamos POR ID ESPECÍFICO
    String expectedCanonicalKey = "canonical/" + fileId + ".csv";

    await().atMost(Duration.ofSeconds(15))
        .pollInterval(Duration.ofMillis(500))
        .untilAsserted(() -> {
          var response = s3Client.listObjects(b -> b.bucket(BUCKET_NAME));

          // Verificar RAW (opcional, por nombre)
          // ...

          // Verificar CANONICAL usando la CLAVE EXACTA
          var canonicalFile = response.contents().stream()
              .filter(o -> o.key().equals(expectedCanonicalKey)) // <--- CAMBIO CLAVE
              .findFirst();

          assertThat(canonicalFile).isPresent();
          // Ahora sí fallará solo si ESTE archivo está mal
          assertThat(canonicalFile.get().size()).isGreaterThan(100 * 1024);
        });
  }
}