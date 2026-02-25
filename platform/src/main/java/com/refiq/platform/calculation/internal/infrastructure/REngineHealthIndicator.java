package com.refiq.platform.calculation.internal.infrastructure;



import java.net.http.HttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class REngineHealthIndicator implements HealthIndicator {

  private final RestClient restClient;
  private final String rEngineUrl;


  public REngineHealthIndicator(
      RestClient.Builder builder,
      @Value("${plumber.api.url}") String rEngineUrl) {

    // 1. Creamos un HttpClient nativo forzando explícitamente la versión HTTP/1.1
    HttpClient httpClient = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_1_1)
        .build();

    // 2. Le pasamos este cliente al RestClient.Builder a través de un RequestFactory
    this.restClient = builder
        .requestFactory(new JdkClientHttpRequestFactory(httpClient))
        .build();

    this.rEngineUrl = rEngineUrl;
  }

  @Override
  public Health health() {
    try {

      String baseUrl = rEngineUrl.endsWith("/") ? rEngineUrl : rEngineUrl + "/";
      String healthEndpoint = baseUrl + "health";

      restClient.get()
          .uri(healthEndpoint)
          .retrieve()
          .toBodilessEntity(); // Verificamos que devuelve un 200 OK

      return Health.up()
          .withDetail("service", "R-Plumber Engine")
          .build();
    } catch (Exception e) {
      return Health.down()
          .withDetail("service", "R-Plumber Engine")
          .withDetail("error", "Cannot connect: " + e.getMessage())
          .build();
    }
  }
}