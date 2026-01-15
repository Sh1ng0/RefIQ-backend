package com.refiq.platform.e2e;

import com.refiq.platform.calculation.api.dto.CalculationRequest;
import com.refiq.platform.calculation.api.dto.CalculationResult;
import com.refiq.platform.calculation.internal.service.CalculationService;
import com.refiq.platform.ingestion.api.dto.IngestionResult;
import com.refiq.platform.ingestion.internal.domain.IngestionFile;
import com.refiq.platform.ingestion.internal.service.IngestionService;
import com.refiq.platform.modules.ingestion.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import software.amazon.awssdk.services.s3.S3Client; // Importante para el compilador
import software.amazon.awssdk.services.s3.model.S3Object;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

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
  private S3Client s3Client; // Inyectamos el cliente para las validaciones del test

  private static final String BUCKET_NAME = "refiq-clinical-data-dev";

  @Test
  void shouldPerformFullAnalysisFromS3UploadToRResult() {
    // 1. ARRANGE
    String csvContent = generateMockCsvContent();
    IngestionFile file = new IngestionFile(
        "e2e-test-data.csv",
        new ByteArrayInputStream(csvContent.getBytes(StandardCharsets.UTF_8)),
        (long) csvContent.length(),
        "text/csv",
        () -> {}
    );

    // 2. ACT - Ingestión
    IngestionResult ingestionResult = ingestionService.ingest(file);
    assertThat(ingestionResult).isInstanceOf(IngestionResult.Success.class);

    String fileId = ((IngestionResult.Success) ingestionResult).response().fileId().toString();
    String s3Key = fileId + "-e2e-test-data.csv";

    // 3. ASSERT - Espera asíncrona (Corrigiendo la sintaxis del SDK v2)
    await()
        .atMost(Duration.ofSeconds(15))
        .untilAsserted(() -> {
          // Usamos listObjectsV2 con el builder lambda
          var listResponse = s3Client.listObjectsV2(req -> req.bucket(BUCKET_NAME));

          boolean exists = listResponse.contents().stream()
              .anyMatch(obj -> obj.key().equals(s3Key));

          assertThat(exists).as("El archivo con key %s debe existir en S3", s3Key).isTrue();
        });

    // 4. ACT - Cálculo
    String testTraceId = "E2E-TEST-001";
    CalculationRequest calcRequest = new CalculationRequest(s3Key, 0.025, 0.975, testTraceId);
    CalculationResult calcResult = calculationService.runAnalysis(calcRequest);

    // 5. ASSERT - Resultado final
    assertThat(calcResult).isInstanceOf(CalculationResult.Success.class);
    var success = (CalculationResult.Success) calcResult;

    assertThat(success.response().labResult().referenceRange()).isNotBlank();


    // NUEVA VALIDACIÓN: Confirmamos la trazabilidad
    // Si R nos devuelve el mismo código, confirmamos que el jefe puede estar tranquilo
    assertThat(success.response().labResult().testCode())
        .as("El código de test debe volver intacto desde R")
        .isEqualTo(testTraceId);

    System.out.println("DEBUG - Rango: " + success.response().labResult().referenceRange());
  }

  private String generateMockCsvContent() {
    // Usamos Locale.US para que el separador decimal sea SIEMPRE un punto (.)
    StringBuilder csv = new StringBuilder("id,age,sex,date,val\n");
    java.util.Random random = new java.util.Random();
    for (int i = 1; i <= 100; i++) {
      double val = 140.0 + (random.nextGaussian() * 5.0);
      csv.append(String.format(java.util.Locale.US, "%d,50,M,2026-01-14,%.2f\n", i, val));
    }
    return csv.toString();
  }
}