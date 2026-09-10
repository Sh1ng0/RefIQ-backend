package com.refiq.platform.calculation.internal.infrastructure;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Checks the health and availability of the external R/Plumber statistical engine.
 * <p>
 * Integrates with Spring Boot Actuator to expose the engine's status
 * via the standard application health endpoint. Employs a fail-fast timeout strategy
 * to prevent monitoring thread exhaustion.
 * </p>
 */
@Component
public class REngineHealthIndicator implements HealthIndicator {

  private final RestClient restClient;
  private final String rEngineUrl;

  public REngineHealthIndicator(
      RestClient.Builder builder,
      @Value("${plumber.api.url}") String rEngineUrl) {

    // Enforce a strict timeout for health checks to avoid blocking Actuator threads
    HttpClient httpClient = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_1_1)
        .connectTimeout(Duration.ofSeconds(2))
        .build();

    JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
    factory.setReadTimeout(Duration.ofSeconds(2));

    this.restClient = builder
        .requestFactory(factory)
        .build();

    this.rEngineUrl = rEngineUrl;
  }

  /**
   * Evaluates the connection to the Plumber API.
   *
   * @return {@link Health#up()} if the engine responds successfully, otherwise {@link Health#down()}.
   */
  @Override
  public Health health() {
    try {
      String baseUrl = rEngineUrl.endsWith("/") ? rEngineUrl : rEngineUrl + "/";
      String healthEndpoint = baseUrl + "health";

      restClient.get()
          .uri(healthEndpoint)
          .retrieve()
          .toBodilessEntity();

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