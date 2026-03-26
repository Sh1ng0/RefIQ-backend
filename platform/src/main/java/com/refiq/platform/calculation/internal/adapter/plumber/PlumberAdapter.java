package com.refiq.platform.calculation.internal.adapter.plumber;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode; // Necesario para leer errores dinámicos
import com.fasterxml.jackson.databind.ObjectMapper;
import com.refiq.platform.calculation.api.dto.CalculationRequest;
import com.refiq.platform.calculation.api.dto.CalculationResponse;
import com.refiq.platform.calculation.internal.port.AnalysisPort;
import com.refiq.platform.calculation.internal.service.CalculationLogEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory; // Para timeouts básicos
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

import java.time.Duration;
import java.util.Map;


/**
 * Secondary adapter implementing communication with the R/Plumber statistical engine.
 * <p>
 * Responsibilities:
 * <ul>
 * <li>Generates S3 Presigned URLs to grant temporary access to the R container.</li>
 * <li>Invokes the Plumber REST API.</li>
 * <li>Translates HTTP errors (timeouts, 422, 500) into domain-specific exceptions.</li>
 * </ul>
 * </p>
 */
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
      @Value("${plumber.presigned.duration-minutes:10}") long durationMinutes,
      @Value("${plumber.timeout.read-seconds:60}") int readTimeoutSeconds) {


    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(5000);
    factory.setReadTimeout(readTimeoutSeconds * 1000);

    this.restClient = builder
        .baseUrl(baseUrl)
        .requestFactory(factory)
        .build();

    this.s3Presigner = s3Presigner;
    this.objectMapper = objectMapper;
    this.bucketName = bucketName;
    this.presignedUrlDuration = Duration.ofMinutes(durationMinutes);
  }


  /**
   * {@inheritDoc}
   * <p>
   * This implementation performs a synchronous HTTP POST to the R container. It handles the
   * response manually to parse specific JSON error messages returned by Plumber.
   * </p>
   *
   * @throws DataInconsistencyException If the engine returns 422 (valid request, invalid data).
   * @throws EngineUnavailableException If the engine returns 500 or cannot be reached.
   */
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

    return restClient.post()
        .uri("/calculate-ri")
        .body(body)
        .exchange((req, res) -> {

          if (res.getStatusCode().is2xxSuccessful()) {
            String successBody = new String(res.getBody().readAllBytes());
            CalculationLogEvent.R_RESPONSE_RECEIVED.log(log, successBody);
            return objectMapper.readValue(successBody, CalculationResponse.class);
          } else {

            String errorBody = new String(res.getBody().readAllBytes());
            String errorReason = extractErrorMessage(errorBody);

            CalculationLogEvent.R_TECHNICAL_ERROR.log(log,
                res.getStatusCode() + " - " + errorReason);


            if (res.getStatusCode().value() == 422) {
              throw new DataInconsistencyException(errorReason);
            } else {
              throw new EngineUnavailableException(
                  "Error R (" + res.getStatusCode() + "): " + errorReason);
            }
          }
        });
  }

  private String extractErrorMessage(String jsonBody) {
    try {
      JsonNode node = objectMapper.readTree(jsonBody);
      if (node.has("error")) {
        return node.get("error").asText();
      }
      return jsonBody; // Fallback
    } catch (Exception e) {
      return "Error desconocido (No JSON): " + jsonBody;
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


  public static class DataInconsistencyException extends RuntimeException {

    public DataInconsistencyException(String msg) {
      super(msg);
    }
  }

  public static class EngineUnavailableException extends RuntimeException {

    public EngineUnavailableException(String msg) {
      super(msg);
    }
  }
}