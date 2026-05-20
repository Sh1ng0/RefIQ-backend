package com.refiq.platform.modules.calculation;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.refiq.platform.calculation.api.dto.CalculationRequest;
import com.refiq.platform.calculation.api.dto.CalculationResult;
import com.refiq.platform.calculation.internal.adapter.plumber.PlumberAdapter;
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

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
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

  private static WireMockServer wireMockServer;

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
  void setUpS3Mock() throws Exception {
    PresignedGetObjectRequest mockPresigned = mock(PresignedGetObjectRequest.class);

    when(mockPresigned.url()).thenReturn(new URL("https://mock-s3-url.com/fake-data.csv"));

    when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class)))
        .thenReturn(mockPresigned);
  }

  @DynamicPropertySource
  static void configureProperties(DynamicPropertyRegistry registry) {
    registry.add("plumber.api.url", () -> wireMockServer.baseUrl());
    registry.add("plumber.timeout.read-seconds", () -> 2);

    registry.add("refiq.storage.s3.bucket-name", () -> "test-bucket");
    registry.add("plumber.presigned.duration-minutes", () -> 10);
  }




  @Test
  @DisplayName("Happy Path: Should correctly deserialize successful R response")
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

    // WHEN
    CalculationRequest request = new CalculationRequest("s3://bucket/valid.csv", null, null, null);
    CalculationResult result = calculationService.runAnalysis(request);

    // THEN
    assertThat(result).isInstanceOf(CalculationResult.Success.class);
    var success = (CalculationResult.Success) result;

    assertThat(success.response().labResult().referenceRange()).isEqualTo("70-100");
    assertThat(success.response().labResult().value()).isEqualTo(95.5);

    verify(postRequestedFor(urlEqualTo("/calculate-ri"))
        .withRequestBody(containing("\"p_low\":0.025"))
        .withRequestBody(containing("\"p_high\":0.975"))
        .withRequestBody(containing("\"test_code\":\"GENERIC\"")));
  }

  @Test
  @DisplayName("Business Error (422): Should map R validation error to DataInconsistency")
  void shouldReturnDataInconsistencyWhenPlumberReturns422() {
    // GIVEN
    String errorJson = "{\"error\": \"Datos insuficientes para RefineR. Válidos encontrados: 5\"}";

    stubFor(post(urlEqualTo("/calculate-ri"))
        .willReturn(aResponse()
            .withStatus(422)
            .withHeader("Content-Type", "application/json")
            .withBody(errorJson)));

    CalculationRequest request = new CalculationRequest("s3://bucket/empty.csv", 0.025, 0.975,
        "TEST-CODE");

    // WHEN
    CalculationResult result = calculationService.runAnalysis(request);

    // THEN
    assertThat(result).isInstanceOf(CalculationResult.DataInconsistency.class);
    var inconsistency = (CalculationResult.DataInconsistency) result;

    assertThat(inconsistency.details())
        .contains("Datos insuficientes")
        .doesNotContain("{");
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

    CalculationRequest request = new CalculationRequest("s3://bucket/crash.csv", 0.025, 0.975,
        null);

    // WHEN
    CalculationResult result = calculationService.runAnalysis(request);

    // THEN
    assertThat(result).isInstanceOf(CalculationResult.EngineUnavailable.class);
    var error = (CalculationResult.EngineUnavailable) result;

    assertThat(error.debugInfo())
        .contains("500")
        .contains("Critical R Error");
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

    CalculationRequest request = new CalculationRequest("s3://bucket/slow.csv", 0.025, 0.975, null);

    // WHEN
    CalculationResult result = calculationService.runAnalysis(request);

    // THEN
    assertThat(result).isInstanceOf(CalculationResult.EngineUnavailable.class);
    var error = (CalculationResult.EngineUnavailable) result;

    assertThat(error.debugInfo())
        .as("Debe indicar que hubo un timeout")
        .containsIgnoringCase("Timeout");
  }
}