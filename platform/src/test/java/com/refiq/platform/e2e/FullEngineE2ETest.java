package com.refiq.platform.e2e;

import com.refiq.platform.calculation.api.dto.CalculationRequest;
import com.refiq.platform.calculation.api.dto.CalculationResult;
import com.refiq.platform.calculation.internal.service.CalculationService;
import com.refiq.platform.ingestion.api.dto.IngestionResult;
import com.refiq.platform.ingestion.internal.domain.IngestionFile;
import com.refiq.platform.ingestion.internal.service.IngestionService;
import com.refiq.platform.modules.ingestion.AbstractIntegrationTest; // Asumo que esto configura S3/Localstack
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import software.amazon.awssdk.services.s3.S3Client;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@ActiveProfiles("test")
@AutoConfigureMockMvc(addFilters = false)
class FullEngineE2ETest extends AbstractIntegrationTest {

  @Autowired
  private IngestionService ingestionService;

  @Autowired
  private CalculationService calculationService;

  @Autowired
  private S3Client s3Client;

  private static final String BUCKET_NAME = "refiq-clinical-data-dev";

  @Test
  @DisplayName("Full E2E: Ingestion (Raw->Canonical) -> R Calculation -> Result")
  void shouldPerformFullAnalysisFromS3UploadToRResult() {

    // ARRANGE
    String csvContent = generateMockCsvContent();

    byte[] contentBytes = csvContent.getBytes(StandardCharsets.UTF_8);

    IngestionFile file = new IngestionFile(
        "e2e-test-data.csv",
        () -> new ByteArrayInputStream(contentBytes),
        (long) contentBytes.length,
        "text/csv",
        () -> {} // Callback vacío para tests (no hay archivo físico que borrar)
    );

    // ACT I
    IngestionResult ingestionResult = ingestionService.ingest(file);
    assertThat(ingestionResult).isInstanceOf(IngestionResult.Success.class);

    String fileId = ((IngestionResult.Success) ingestionResult).response().fileId().toString();


    String expectedRawPrefix = "raw/" + fileId; // El servicio añade "-filename"
    String expectedCanonicalKey = "canonical/" + fileId + ".csv";

    // ASSERT I
    await()
        .atMost(Duration.ofSeconds(15))
        .pollInterval(Duration.ofMillis(500))
        .untilAsserted(() -> {
          // Listar bucket
          var listResponse = s3Client.listObjectsV2(req -> req.bucket(BUCKET_NAME));

          // A) Verificar que el RAW existe (Traza de auditoría)
          boolean rawExists = listResponse.contents().stream()
              .anyMatch(obj -> obj.key().startsWith(expectedRawPrefix));
          assertThat(rawExists).as("El archivo RAW debe existir en S3").isTrue();

          // B) Verificar que el CANONICAL existe (Input para R)
          boolean canonicalExists = listResponse.contents().stream()
              .anyMatch(obj -> obj.key().equals(expectedCanonicalKey));
          assertThat(canonicalExists).as("El archivo CANONICAL debe existir en S3").isTrue();
        });

    // ACT II
    String testTraceId = "E2E-TEST-001";

    // Usamos el constructor limpio del Record
    CalculationRequest calcRequest = new CalculationRequest(
        expectedCanonicalKey,
        0.025,
        0.975,
        testTraceId
    );

    CalculationResult calcResult = calculationService.runAnalysis(calcRequest);

    // ASSERT II
    assertThat(calcResult).isInstanceOf(CalculationResult.Success.class);
    var success = (CalculationResult.Success) calcResult;


    assertThat(success.response().labResult().referenceRange())
        .as("R debe devolver un rango calculado (ej. '130.5 - 150.2')")
        .isNotBlank()
        .contains("-");

    assertThat(success.response().labResult().testCode())
        .as("El código de test debe sobrevivir el viaje de ida y vuelta a R")
        .isEqualTo(testTraceId);


    System.out.println("✅ E2E PASSED. Rango: " + success.response().labResult().referenceRange());
  }

  /**
   * Genera un CSV compatible.
   * Uso Locale.GERMAN para forzar comas decimales (e.g. "140,50") y probar el Normalizador.
   */
  private String generateMockCsvContent() {
    StringBuilder csv = new StringBuilder();
    csv.append("ID;Age;DateOfBirth;Sex;ValueOriginalResult;ValueResult\n");

    java.util.Random random = new java.util.Random();
    for (int i = 1; i <= 50; i++) {
      double val = 140.0 + (random.nextGaussian() * 5.0);
      csv.append(String.format(java.util.Locale.GERMAN,
          "%d;%d;1980-01-01;M;%.2f;%.2f\n",
          i,
          18000 + i,
          val,
          val));
    }
    return csv.toString();
  }
}