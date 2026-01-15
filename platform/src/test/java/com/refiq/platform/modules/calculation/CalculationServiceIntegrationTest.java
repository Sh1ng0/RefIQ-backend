package com.refiq.platform.modules.calculation;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.refiq.platform.calculation.api.dto.CalculationRequest;
import com.refiq.platform.calculation.api.dto.CalculationResult;
import com.refiq.platform.calculation.internal.service.CalculationService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class CalculationServiceIntegrationTest {

  @Autowired
  private CalculationService calculationService;

  private static WireMockServer wireMockServer;

  @BeforeAll
  static void startWireMock() {
    wireMockServer = new WireMockServer(wireMockConfig().dynamicPort());
    wireMockServer.start();

    WireMock.configureFor("localhost", wireMockServer.port());


    System.setProperty("wiremock.server.port", String.valueOf(wireMockServer.port()));
  }

  @AfterAll
  static void stopWireMock() {
    if (wireMockServer != null) {
      wireMockServer.stop();
    }
  }

  @Test
  void shouldReturnSuccessfulCalculationWhenPlumberResponds() {

    String responseBody = """
        {
          "lab_result": {
            "test_code": "2345-7",
            "name": "Glucose",
            "value": null,
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


    CalculationRequest request = new CalculationRequest("s3://bucket/test.csv", 0.025, 0.975, null);
    CalculationResult result = calculationService.runAnalysis(request);


    assertThat(result).isInstanceOf(CalculationResult.Success.class);
    var success = (CalculationResult.Success) result;
    assertThat(success.response().labResult().referenceRange()).isEqualTo("70-100");

    verify(postRequestedFor(urlEqualTo("/calculate-ri"))

        .withRequestBody(containing("\"test_code\":\"GENERIC\""))

        .withRequestBody(containing("\"p_low\":0.025"))

        .withRequestBody(containing("test.csv"))

        .withRequestBody(containing("\"data_url\":")));
  }

  @Test
  void shouldReturnEngineUnavailableWhenPlumberReturnsError() {
    stubFor(post(urlEqualTo("/calculate-ri"))
        .willReturn(aResponse()
            .withStatus(500)
            .withHeader("Content-Type", "application/json")
            .withBody("{\"error\": \"R execution failed\"}")));

    CalculationRequest request = new CalculationRequest("s3://bucket/test.csv", 0.025, 0.975, null);
    CalculationResult result = calculationService.runAnalysis(request);

    assertThat(result).isInstanceOf(CalculationResult.EngineUnavailable.class);
    var error = (CalculationResult.EngineUnavailable) result;

    assertThat(error.debugInfo()).contains("500");
  }
}