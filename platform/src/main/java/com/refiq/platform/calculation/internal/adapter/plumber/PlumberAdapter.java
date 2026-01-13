package com.refiq.platform.calculation.internal.adapter.plumber;

import com.refiq.platform.calculation.api.dto.CalculationRequest;
import com.refiq.platform.calculation.api.dto.CalculationResponse;
import com.refiq.platform.calculation.internal.port.AnalysisPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class PlumberAdapter implements AnalysisPort {

  private final RestClient restClient;

  // La URL vendría de application.properties (ej. http://localhost:8000)
  public PlumberAdapter(RestClient.Builder builder, @Value("${plumber.api.url}") String baseUrl) {
    this.restClient = builder.baseUrl(baseUrl).build();
  }

  @Override
  public CalculationResponse calculate(CalculationRequest request) {
    return restClient.post()
        .uri("/calculate-ri")
        .body(request)
        .retrieve()
        .onStatus(status -> status.isError(), (req, res) -> {
          throw new RuntimeException("Error en el motor R: " + res.getStatusCode());
        })
        .body(CalculationResponse.class);
  }

}