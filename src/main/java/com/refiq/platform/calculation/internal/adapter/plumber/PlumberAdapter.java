package com.refiq.platform.calculation.internal.adapter.plumber;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.refiq.platform.calculation.api.dto.CalculationRequest;
import com.refiq.platform.calculation.api.dto.CalculationResponse;
import com.refiq.platform.calculation.internal.logging.CalculationLogEvent; // <-- Nuevo import
import com.refiq.platform.calculation.internal.port.AnalysisPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

import java.time.Duration;
import java.util.Map;

/**
 * Implements communication with the R/Plumber statistical engine as a secondary adapter.
 * <p>
 * Responsibilities:
 * <ul>
 * <li>Generates S3 Presigned URLs to grant temporary access to the R container.</li>
 * <li>Invokes the Plumber REST API.</li>
 * <li>Translates HTTP errors (timeouts, 422, 500) into domain-specific exceptions.</li>
 * </ul>
 * </p>
 * <p>
 * <strong>Architectural Note:</strong> Retry logic for transient network failures is intentionally
 * delegated to the Spring framework ({@code @Retryable}) for simplicity and to avoid reinventing
 * standard resilience patterns, maintaining the adapter's focus on payload translation.
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
      RestClient plumberRestClient,
      S3Presigner s3Presigner,
      ObjectMapper objectMapper,
      @Value("${refiq.storage.s3.bucket-name}") String bucketName,
      @Value("${plumber.presigned.duration-minutes:10}") long durationMinutes) {

    this.restClient = plumberRestClient;
    this.s3Presigner = s3Presigner;
    this.objectMapper = objectMapper;
    this.bucketName = bucketName;
    this.presignedUrlDuration = Duration.ofMinutes(durationMinutes);
  }

  /**
   * {@inheritDoc}
   * <p>
   * Performs a synchronous HTTP POST to the R container. Tolerates transient network
   * failures and 500 errors by retrying locally with an exponential backoff.
   * </p>
   *
   * @throws DataInconsistencyException If the engine returns 422 (valid request, invalid data). Not retryable.
   * @throws EngineUnavailableException If the engine returns 500 or cannot be reached after all retries.
   */
  @Retryable(
      retryFor = { Exception.class },
      noRetryFor = { DataInconsistencyException.class },
      maxAttempts = 3,
      backoff = @Backoff(delay = 2000, multiplier = 2)
  )
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

    // CORRECCIÓN 1
    new CalculationLogEvent.AnalysisInitiated(request.s3Key()).log(log);

    return restClient.post()
        .uri("/calculate-ri")
        .body(body)
        .exchange((req, res) -> {

          if (res.getStatusCode().is2xxSuccessful()) {
            String successBody = new String(res.getBody().readAllBytes());
            // CORRECCIÓN 2
            new CalculationLogEvent.RResponseReceived(successBody).log(log);
            return objectMapper.readValue(successBody, CalculationResponse.class);
          } else {

            String errorBody = new String(res.getBody().readAllBytes());
            String errorReason = extractErrorMessage(errorBody);

            // CORRECCIÓN 3
            new CalculationLogEvent.RTechnicalError(res.getStatusCode() + " - " + errorReason).log(log);

            if (res.getStatusCode().value() == 422) {
              throw new DataInconsistencyException(errorReason);
            } else {
              throw new EngineUnavailableException("R Error (" + res.getStatusCode() + "): " + errorReason);
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
      return "Unknown error (Not JSON): " + jsonBody;
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