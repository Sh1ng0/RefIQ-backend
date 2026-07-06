package com.refiq.platform.modules.calculation;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.refiq.platform.calculation.api.dto.CalculationRequest;
import com.refiq.platform.calculation.api.dto.CalculationResult;
import com.refiq.platform.calculation.internal.adapter.plumber.PlumberAdapter;
import com.refiq.platform.calculation.internal.repository.CalculationResultRepository;
import com.refiq.platform.calculation.internal.repository.entity.CalculationResultEntity;
import com.refiq.platform.calculation.internal.repository.entity.CalculationStatus;
import com.refiq.platform.calculation.internal.service.CalculationService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.net.URL;
import java.util.Optional;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = {
    CalculationService.class,
    PlumberAdapter.class
})
@Import({
    RestClientAutoConfiguration.class,
    JacksonAutoConfiguration.class,
    HttpMessageConvertersAutoConfiguration.class
})
@ActiveProfiles("test")
class CalculationServiceIntegrationTest {

  @Autowired
  private CalculationService calculationService;

  @MockitoBean
  private S3Presigner s3Presigner;

  // NUEVO: Mockeamos el repositorio para la persistencia del resultado
  @MockitoBean
  private CalculationResultRepository calculationResultRepository;

  private static WireMockServer wireMockServer;

  // UUID de prueba constante
  private final UUID testUuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");

  @BeforeAll
  static void startWireMock() {
    wireMockServer = new WireMockServer(wireMockConfig().dynamicPort());
    wireMockServer.start();
    WireMock.configureFor("localhost", wireMockServer.port());
  }

  @AfterAll
  static void stopWireMock() {
    if (wireMockServer != null) {
      wireMockServer.stop();
    }
  }

  @BeforeEach
  void setUpMocks() throws Exception {
    // Configuración de S3
    PresignedGetObjectRequest mockPresigned = mock(PresignedGetObjectRequest.class);
    when(mockPresigned.url()).thenReturn(new URL("https://mock-s3-url.com/fake-data.csv"));
    when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class)))
        .thenReturn(mockPresigned);

    // NUEVO: Simulamos que la entidad en estado PENDING ya existe en la BD
    CalculationResultEntity dummyEntity = CalculationResultEntity.builder()
        .id(testUuid)
        .status(CalculationStatus.PENDING)
        .build();
    when(calculationResultRepository.findById(testUuid)).thenReturn(Optional.of(dummyEntity));
  }

  @DynamicPropertySource
  static void configureProperties(DynamicPropertyRegistry registry) {
    registry.add("plumber.api.url", () -> wireMockServer.baseUrl());
    registry.add("plumber.timeout.read-seconds", () -> 2);
    registry.add("refiq.storage.s3.bucket-name", () -> "test-bucket");
    registry.add("plumber.presigned.duration-minutes", () -> 10);
  }

  @Test
  @DisplayName("Happy Path: Should correctly deserialize successful R response and save to DB")
  void shouldReturnSuccessfulCalculationWhenPlumberResponds() {
    // GIVEN
    String responseBody = """
        {
          "lab_result": {
            "test_code": "2345-7",
            "name": "Glucose",
            "value": 95.5,
            "unit": "mg/dL",
            "reference_range": "70-100",
            "notes": "Valor de referencia calculado"
          },
          "parameters": {
            "weight_factor": 1.0
          }
        }
        """;

    stubFor(post(urlEqualTo("/calculate-ri"))
        .willReturn(aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(responseBody)));

    // Ruta Gold válida con UUID inyectado
    String s3Key = "3.Gold/GENERIC/GENERIC_" + testUuid + ".parquet";
    CalculationRequest request = new CalculationRequest(s3Key, null, null, null);

    // WHEN
    CalculationResult result = calculationService.runAnalysis(request);

    // THEN
    assertThat(result).isInstanceOf(CalculationResult.Success.class);
    var success = (CalculationResult.Success) result;

    assertThat(success.response().labResult().referenceRange()).isEqualTo("70-100");
    assertThat(success.response().labResult().value()).isEqualTo(95.5);

    // Verificamos que se llamó al repositorio para actualizar el estado
    verify(calculationResultRepository).save(any(CalculationResultEntity.class));
  }

  @Test
  @DisplayName("Business Error (422): Should map R validation error to DataInconsistency and save to DB")
  void shouldReturnDataInconsistencyWhenPlumberReturns422() {
    // GIVEN
    String errorJson = "{\"error\": \"Datos insuficientes para RefineR. Válidos encontrados: 5\"}";

    stubFor(post(urlEqualTo("/calculate-ri"))
        .willReturn(aResponse()
            .withStatus(422)
            .withHeader("Content-Type", "application/json")
            .withBody(errorJson)));

    String s3Key = "3.Gold/TEST/TEST_" + testUuid + ".parquet";
    CalculationRequest request = new CalculationRequest(s3Key, 0.025, 0.975, "TEST-CODE");

    // WHEN
    CalculationResult result = calculationService.runAnalysis(request);

    // THEN
    assertThat(result).isInstanceOf(CalculationResult.DataInconsistency.class);

    // Verificamos que se intentó guardar el error en base de datos
    verify(calculationResultRepository).save(any(CalculationResultEntity.class));
  }

  @Test
  @DisplayName("Technical Error (500): Should map R crash to EngineUnavailable")
  void shouldReturnEngineUnavailableWhenPlumberReturns500() {
    // GIVEN
    String errorJson = "{\"error\": \"Critical R Error: Memory allocation failed\"}";

    stubFor(post(urlEqualTo("/calculate-ri"))
        .willReturn(aResponse()
            .withStatus(500)
            .withHeader("Content-Type", "application/json")
            .withBody(errorJson)));

    String s3Key = "3.Gold/TEST/TEST_" + testUuid + ".parquet";
    CalculationRequest request = new CalculationRequest(s3Key, 0.025, 0.975, null);

    // WHEN
    CalculationResult result = calculationService.runAnalysis(request);

    // THEN
    assertThat(result).isInstanceOf(CalculationResult.EngineUnavailable.class);
    verify(calculationResultRepository).save(any(CalculationResultEntity.class));
  }

  @Test
  @DisplayName("Network Timeout: Should gracefully handle R engine latency")
  void shouldReturnEngineUnavailableOnTimeout() {
    // GIVEN
    stubFor(post(urlEqualTo("/calculate-ri"))
        .willReturn(aResponse()
            .withStatus(200)
            .withBody("{}")
            .withFixedDelay(3000)));

    String s3Key = "3.Gold/TEST/TEST_" + testUuid + ".parquet";
    CalculationRequest request = new CalculationRequest(s3Key, 0.025, 0.975, null);

    // WHEN
    CalculationResult result = calculationService.runAnalysis(request);

    // THEN
    assertThat(result).isInstanceOf(CalculationResult.EngineUnavailable.class);
    verify(calculationResultRepository).save(any(CalculationResultEntity.class));
  }
}