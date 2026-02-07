package com.refiq.platform.e2e;

import com.refiq.platform.calculation.api.dto.CalculationRequest;
import com.refiq.platform.calculation.api.dto.CalculationResult;
import com.refiq.platform.calculation.internal.service.CalculationService;
import com.refiq.platform.ingestion.api.dto.IngestionResult;
import com.refiq.platform.ingestion.internal.domain.IngestionFile;
import com.refiq.platform.ingestion.internal.service.IngestionService;
import com.refiq.platform.modules.ingestion.AbstractIntegrationTest; // Asumo que esto configura S3/Localstack
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
  void shouldPerformFullAnalysisFromS3UploadToRResult() {

    String csvContent = generateMockCsvContent();

    // ARRANGE

    IngestionFile file = new IngestionFile(
        "e2e-test-data.csv",

        () -> new ByteArrayInputStream(csvContent.getBytes(StandardCharsets.UTF_8)),
        (long) csvContent.length(),
        "text/csv",
        () -> {
        } // Callback vacío para tests
    );

    // ACT
    IngestionResult ingestionResult = ingestionService.ingest(file);
    assertThat(ingestionResult).isInstanceOf(IngestionResult.Success.class);

    String fileId = ((IngestionResult.Success) ingestionResult).response().fileId().toString();

    // La ruta raw sería "raw/" + fileId + "-e2e-test-data.csv"
    String canonicalS3Key = "canonical/" + fileId + ".csv";

    // ASSERT
    await()
        .atMost(Duration.ofSeconds(15))
        .pollInterval(Duration.ofMillis(500))
        .untilAsserted(() -> {

          var listResponse = s3Client.listObjectsV2(
              req -> req.bucket(BUCKET_NAME).prefix("canonical/"));

          boolean exists = listResponse.contents().stream()
              .anyMatch(obj -> obj.key().equals(canonicalS3Key));

          assertThat(exists)
              .as("El archivo canónico %s debe haber sido generado y subido a S3", canonicalS3Key)
              .isTrue();
        });

    // ACT II
    String testTraceId = "E2E-TEST-001";


    CalculationRequest calcRequest = new CalculationRequest(canonicalS3Key, 0.025, 0.975,
        testTraceId);


    CalculationResult calcResult = calculationService.runAnalysis(calcRequest);

    // ASSERT II
    assertThat(calcResult).isInstanceOf(CalculationResult.Success.class);
    var success = (CalculationResult.Success) calcResult;

    assertThat(success.response().labResult().referenceRange())
        .as("R debe haber calculado un rango válido")
        .isNotBlank();

    assertThat(success.response().labResult().testCode())
        .as("El código de test debe volver intacto desde R")
        .isEqualTo(testTraceId);

    System.out.println(
        "DEBUG - Rango Calculado por R: " + success.response().labResult().referenceRange());
  }

  /**
   * Genera un CSV compatible con el CsvNormalizer actual. Formato:
   * ID;Age;DateOfBirth;Sex;ValueOriginalResult;ValueResult
   */
  private String generateMockCsvContent() {
    StringBuilder csv = new StringBuilder();

    csv.append("ID;Age;DateOfBirth;Sex;ValueOriginalResult;ValueResult\n");

    java.util.Random random = new java.util.Random();
    for (int i = 1; i <= 50; i++) {
      double val = 140.0 + (random.nextGaussian() * 5.0);


      csv.append(String.format(java.util.Locale.GERMAN, // Locale German usa comas para decimales
          "%d;%d;1980-01-01;M;%.2f;%.2f\n",
          i,
          18000 + i,
          val,
          val));
    }
    return csv.toString();
  }
}