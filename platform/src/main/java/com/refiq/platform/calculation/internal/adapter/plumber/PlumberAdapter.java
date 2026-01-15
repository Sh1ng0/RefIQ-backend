package com.refiq.platform.calculation.internal.adapter.plumber;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.refiq.platform.calculation.api.dto.CalculationRequest;
import com.refiq.platform.calculation.api.dto.CalculationResponse;
import com.refiq.platform.calculation.internal.port.AnalysisPort;
import com.refiq.platform.calculation.internal.service.CalculationLogEvent; // Importamos el Enum
import java.time.Duration;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

@Component
public class PlumberAdapter implements AnalysisPort {


  private static final Logger log = LoggerFactory.getLogger(PlumberAdapter.class);

  private final RestClient restClient;
  private final S3Presigner s3Presigner;
  private final ObjectMapper objectMapper;
  private final String bucketName;
  private final Duration presignedUrlDuration;

  public PlumberAdapter(
      RestClient.Builder builder,
      S3Presigner s3Presigner,
      ObjectMapper objectMapper,
      @Value("${plumber.api.url}") String baseUrl,
      @Value("${refiq.storage.s3.bucket-name}") String bucketName,
      @Value("${plumber.presigned.duration-minutes:10}") long durationMinutes) {

    this.restClient = builder.baseUrl(baseUrl).build();
    this.s3Presigner = s3Presigner;
    this.objectMapper = objectMapper;
    this.bucketName = bucketName;
    this.presignedUrlDuration = Duration.ofMinutes(durationMinutes);
  }

  @Override
  public CalculationResponse calculate(CalculationRequest request) {
    String presignedUrl = generatePresignedUrl(request.s3Key());

    String safeTestCode = request.testCode() != null ? request.testCode() : "GENERIC";

    Map<String, Object> body = Map.of(
        "data_url", presignedUrl,
        "p_low", request.percentileLow(),
        "p_high", request.percentileHigh(),
        "test_code", safeTestCode
    );


    CalculationLogEvent.ANALYSIS_INITIATED.log(log, request.s3Key());

    String rawJson = restClient.post()
        .uri("/calculate-ri")
        .body(body)
        .retrieve()
        .onStatus(status -> status.isError(), (req, res) -> {

          CalculationLogEvent.R_TECHNICAL_ERROR.log(log, res.getStatusCode());
          throw new RuntimeException("Error técnico en motor R. Status: " + res.getStatusCode());
        })
        .body(String.class);

    try {
      CalculationLogEvent.R_RESPONSE_RECEIVED.log(log, rawJson);
      return objectMapper.readValue(rawJson, CalculationResponse.class);
    } catch (JsonProcessingException e) {
      // Este JSON sería uno fallido, sirve para debug
      CalculationLogEvent.R_DESERIALIZATION_ERROR.log(log, rawJson);
      throw new RuntimeException("Error de formato en respuesta del motor de cálculo", e);
    }
  }

  private String generatePresignedUrl(String key) {
    GetObjectRequest getObjectRequest = GetObjectRequest.builder()
        .bucket(bucketName)
        .key(key)
        .build();

    GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
        .signatureDuration(presignedUrlDuration)
        .getObjectRequest(getObjectRequest)
        .build();

    return s3Presigner.presignGetObject(presignRequest).url().toString();
  }
}