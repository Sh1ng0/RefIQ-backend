package com.refiq.platform.calculation.internal.service;



import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

import com.refiq.platform.calculation.api.dto.CalculationRequest;
import com.refiq.platform.calculation.api.dto.CalculationResponse;
import com.refiq.platform.calculation.api.dto.CalculationResult;
import com.refiq.platform.calculation.internal.port.AnalysisPort;
import com.refiq.platform.calculation.internal.repository.CalculationResultRepository;
import com.refiq.platform.calculation.internal.repository.entity.CalculationResultEntity;
import com.refiq.platform.calculation.internal.repository.entity.CalculationStatus;
import com.refiq.platform.ingestion.api.event.FileAcceptedEvent;
import com.refiq.platform.support.slices.BasePostgresTest;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@ApplicationModuleTest
@ActiveProfiles("test")
@DisplayName("Calculation - Service & Persistence (Postgres Integration)")
class CalculationServiceIntegrationTest extends BasePostgresTest {

  @Autowired
  private CalculationService calculationService;

  @Autowired
  private CalculationResultRepository repository;

  @Autowired
  private CalculationTrackingListener trackingListener;

  // Mockeamos la API externa porque ya la testeamos en el PlumberAdapterTest
  @MockitoBean
  private AnalysisPort analysisPort;

  @MockitoBean(name = "webhookExecutor")
  private java.util.concurrent.ExecutorService webhookExecutor;

  @Test
  @DisplayName("Listener: Debe crear un registro PENDING al recibir FileAcceptedEvent de Ingestion")
  void shouldCreatePendingRecordOnEvent() {
    // GIVEN
    UUID fileId = UUID.randomUUID();
    FileAcceptedEvent event = new FileAcceptedEvent(fileId, "TSH");

    // WHEN
    trackingListener.on(event);

    // THEN: Esperamos de forma reactiva a que el hilo asíncrono [task-1] termine de guardar en BD
    await()
        .atMost(Duration.ofSeconds(3))
        .pollInterval(Duration.ofMillis(100))
        .untilAsserted(() -> {
          Optional<CalculationResultEntity> saved = repository.findById(fileId);
          assertThat(saved).isPresent();
          assertThat(saved.get().getStatus()).isEqualTo(CalculationStatus.PENDING);
        });
  }

  @Test
  @DisplayName("runAnalysis: Debe procesar exitosamente, guardar el JSON y actualizar a SUCCESS")
  void shouldProcessAndSaveSuccess() {
    // GIVEN
    UUID fileId = UUID.randomUUID();
    // Simulamos que el evento ya creó la entidad en la BD
    repository.save(CalculationResultEntity.builder()
        .id(fileId)
        .status(CalculationStatus.PENDING)
        .build());

    String s3Key = "3.Gold/TSH/" + fileId + "-data.parquet";
    CalculationRequest request = new CalculationRequest(s3Key, 0.025, 0.975, "TSH");

    // R nos responde con éxito
    CalculationResponse.LabResult labResult = new CalculationResponse.LabResult(
        "TSH", "Analysis", 2.5, "mIU/L", "0.5-4.0", "OK"
    );
    when(analysisPort.calculate(any())).thenReturn(new CalculationResponse(labResult, Map.of()));

    // WHEN
    CalculationResult result = calculationService.runAnalysis(request);

    // THEN
    assertThat(result).isInstanceOf(CalculationResult.Success.class);

    // Verificamos que Postgres se actualizó correctamente
    CalculationResultEntity entity = repository.findById(fileId).get();
    assertThat(entity.getStatus()).isEqualTo(CalculationStatus.SUCCESS);
    assertThat(entity.getPayload()).contains("0.5-4.0"); // Verifica que el JSON se serializó bien
  }

  @Test
  @DisplayName("runAnalysis: Idempotencia - Debe abortar devolviendo AlreadyHandled si el cálculo ya se hizo")
  void shouldReturnAlreadyHandledIfClaimed() {
    // GIVEN
    UUID fileId = UUID.randomUUID();
    // Simulamos que un webhook anterior ya lo procesó y está en SUCCESS
    repository.save(CalculationResultEntity.builder()
        .id(fileId)
        .status(CalculationStatus.SUCCESS)
        .payload("{}")
        .build());

    String s3Key = "3.Gold/TSH/" + fileId + "-data.parquet";
    CalculationRequest request = new CalculationRequest(s3Key, 0.025, 0.975, "TSH");

    // WHEN
    CalculationResult result = calculationService.runAnalysis(request);

    // THEN
    assertThat(result).isInstanceOf(CalculationResult.AlreadyHandled.class);
    CalculationResult.AlreadyHandled handled = (CalculationResult.AlreadyHandled) result;
    assertThat(handled.status()).isEqualTo("SUCCESS");

    // CRÍTICO: Verificamos que NUNCA llamó al motor de R (ahorro de costes e idempotencia)
    verify(analysisPort, never()).calculate(any());
  }
}