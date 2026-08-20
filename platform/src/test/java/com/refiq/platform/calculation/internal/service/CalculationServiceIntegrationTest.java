package com.refiq.platform.calculation.internal.service;

import static com.refiq.platform.shared.db.generated.Tables.CALCULATION_RESULTS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

import com.refiq.platform.calculation.api.dto.CalculationRequest;
import com.refiq.platform.calculation.api.dto.CalculationResponse;
import com.refiq.platform.calculation.api.dto.CalculationResult;
import com.refiq.platform.calculation.internal.domain.CalculationState;
import com.refiq.platform.calculation.internal.port.AnalysisPort;
import com.refiq.platform.calculation.internal.repository.DbCalculationResultRepository;
import com.refiq.platform.ingestion.api.event.FileAcceptedEvent;
import com.refiq.platform.support.slices.BasePostgresTest;
import com.refiq.platform.support.slices.RefiqModuleTest;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@RefiqModuleTest
@DisplayName("Calculation - Service & Persistence (Postgres Integration)")
class CalculationServiceIntegrationTest extends BasePostgresTest {

  @Autowired
  private CalculationService calculationService;

  @Autowired
  private DbCalculationResultRepository repository;

  @Autowired
  private CalculationTrackingListener trackingListener;

  @Autowired
  private DSLContext dsl;

  @MockitoBean
  private AnalysisPort analysisPort;

  @MockitoBean(name = "webhookExecutor")
  private java.util.concurrent.ExecutorService webhookExecutor;

  @AfterEach
  void cleanUp() {
    dsl.deleteFrom(CALCULATION_RESULTS).execute();
  }

  @Test
  @DisplayName("Listener: Debe crear un registro PENDING al recibir FileAcceptedEvent de Ingestion")
  void shouldCreatePendingRecordOnEvent() {
    // GIVEN
    UUID fileId = UUID.randomUUID();
    FileAcceptedEvent event = new FileAcceptedEvent(fileId, "TSH");

    // WHEN
    trackingListener.on(event);

    // THEN
    await()
        .atMost(Duration.ofSeconds(3))
        .pollInterval(Duration.ofMillis(100))
        .untilAsserted(() -> {
          Optional<CalculationState> saved = repository.findById(fileId);
          assertThat(saved).isPresent();
          // Verificamos el tipo de la interfaz sellada directamente
          assertThat(saved.get()).isInstanceOf(CalculationState.Pending.class);
        });
  }

  @Test
  @DisplayName("runAnalysis: Debe procesar exitosamente, guardar el JSON y actualizar a SUCCESS")
  void shouldProcessAndSaveSuccess() {
    // GIVEN
    UUID fileId = UUID.randomUUID();

    repository.insert(new CalculationState.Pending(fileId));

    String s3Key = "3.Gold/TSH/" + fileId + "-data.parquet";
    CalculationRequest request = new CalculationRequest(s3Key, 0.025, 0.975, "TSH");

    CalculationResponse.LabResult labResult = new CalculationResponse.LabResult(
        "TSH", "Analysis", 2.5, "mIU/L", "0.5-4.0", "OK"
    );
    when(analysisPort.calculate(any())).thenReturn(new CalculationResponse(labResult, Map.of()));

    // WHEN
    CalculationResult result = calculationService.runAnalysis(request);

    // THEN
    assertThat(result).isInstanceOf(CalculationResult.Success.class);

    CalculationState state = repository.findById(fileId).orElseThrow();

    assertThat(state).isInstanceOf(CalculationState.Success.class);

    CalculationState.Success successState = (CalculationState.Success) state;
    assertThat(successState.payload()).contains("0.5-4.0"); // Verifica el JSON
  }

  @Test
  @DisplayName("runAnalysis: Idempotencia - Debe abortar devolviendo AlreadyHandled si el cálculo ya se hizo")
  void shouldReturnAlreadyHandledIfClaimed() {
    // GIVEN
    UUID fileId = UUID.randomUUID();

    repository.insert(new CalculationState.Pending(fileId));
    repository.update(new CalculationState.Success(fileId, "{}"));

    String s3Key = "3.Gold/TSH/" + fileId + "-data.parquet";
    CalculationRequest request = new CalculationRequest(s3Key, 0.025, 0.975, "TSH");

    // WHEN
    CalculationResult result = calculationService.runAnalysis(request);

    // THEN
    assertThat(result).isInstanceOf(CalculationResult.AlreadyHandled.class);
    CalculationResult.AlreadyHandled handled = (CalculationResult.AlreadyHandled) result;
    assertThat(handled.status()).isEqualTo("SUCCESS");

    // We have to ascertain that R was not invoked
    verify(analysisPort, never()).calculate(any());
  }
}