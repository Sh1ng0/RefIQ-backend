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
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;
import org.springframework.test.context.ActiveProfiles;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
//@AutoConfigureWireMock(port = 0) // Levanta WireMock en un puerto libre
@ActiveProfiles("test")
class CalculationServiceIntegrationTest {

  @Autowired
  private CalculationService calculationService;

  private static WireMockServer wireMockServer;

  @BeforeAll
  static void startWireMock() {
    // Usamos wireMockConfig() en lugar de options()
    wireMockServer = new WireMockServer(wireMockConfig().dynamicPort());
    wireMockServer.start();

    // Conectamos el cliente estático al servidor manual
    WireMock.configureFor("localhost", wireMockServer.port());

    // Inyectamos el puerto en el sistema para que Spring lo lea
    System.setProperty("wiremock.server.port", String.valueOf(wireMockServer.port()));
  }

  @AfterAll
  static void stopWireMock() {
    if (wireMockServer != null) wireMockServer.stop();
  }

  @Test
  void shouldReturnSuccessfulCalculationWhenPlumberResponds() {
    // 1. Definimos el "Mock" del JSON que nos dio el jefe
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

    // 2. Configuramos WireMock para interceptar la llamada
    stubFor(post(urlEqualTo("/calculate-ri"))
        .willReturn(aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(responseBody)));

    // 3. Ejecutamos nuestro servicio
    CalculationRequest request = new CalculationRequest("s3://bucket/test.csv", 0.025, 0.975);
    CalculationResult result = calculationService.runAnalysis(request);

    // 4. Verificamos que Java ha procesado bien el "puente"
    assertThat(result).isInstanceOf(CalculationResult.Success.class);
    var success = (CalculationResult.Success) result;
    assertThat(success.response().labResult().referenceRange()).isEqualTo("70-100");

    // Verificamos que WireMock recibió lo que esperábamos
    verify(postRequestedFor(urlEqualTo("/calculate-ri"))
        .withRequestBody(containing("s3://bucket/test.csv")));
  }



  @Test
  void shouldReturnEngineUnavailableWhenPlumberReturnsError() {
    // 1. Configuramos WireMock para que falle (Simulamos un 500 o que el servicio no responde bien)
    stubFor(post(urlEqualTo("/calculate-ri"))
        .willReturn(aResponse()
            .withStatus(500)
            .withHeader("Content-Type", "application/json")
            .withBody("{\"error\": \"R execution failed\"}")));

    // 2. Ejecutamos nuestro servicio
    CalculationRequest request = new CalculationRequest("s3://bucket/test.csv", 0.025, 0.975);
    CalculationResult result = calculationService.runAnalysis(request);

    // 3. Verificamos que Java captura el error técnico y lo envuelve en EngineUnavailable
    assertThat(result).isInstanceOf(CalculationResult.EngineUnavailable.class);

    var error = (CalculationResult.EngineUnavailable) result;
    // Verificamos que el mensaje de error técnico se propaga para debug
    assertThat(error.debugInfo()).contains("500");

    // Verificamos en tus logs que se registró el error de Plumber
    // (Esto lo verás en la consola gracias a tu CalculationLogEvent.PLUMBER_ERROR)
  }
}