package com.refiq.platform.calculation.internal.adapter.plumber;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.refiq.platform.calculation.api.dto.CalculationRequest;
import com.refiq.platform.calculation.api.dto.CalculationResponse;
import java.net.URL;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

@ActiveProfiles("test")
@RestClientTest(
    components = PlumberAdapter.class,
    properties = {
        "plumber.api.url=http://fake-plumber",
        "refiq.storage.s3.bucket-name=test-bucket",
        "plumber.presigned.duration-minutes=10",
        "plumber.timeout.read-seconds=1"
    }
)
@Import({AopAutoConfiguration.class, PlumberAdapterTest.PlumberTestConfig.class})
@DisplayName("Calculation - Plumber Adapter (RestClientTest)")
class PlumberAdapterTest {

  @Autowired
  private PlumberAdapter plumberAdapter;

  @Autowired
  private MockRestServiceServer mockServer;

  @MockitoBean
  private S3Presigner s3Presigner;

  @TestConfiguration
  @EnableRetry
  static class PlumberTestConfig {

    @Bean
    public RestClient plumberRestClient(
        RestClient.Builder builder,
        @org.springframework.beans.factory.annotation.Value("${plumber.api.url}") String baseUrl) {
      return builder.baseUrl(baseUrl).build();
    }
  }

  @BeforeEach
  void setUp() throws Exception {
    mockServer.reset();

    PresignedGetObjectRequest mockPresignedReq = mock(PresignedGetObjectRequest.class);
    when(mockPresignedReq.url()).thenReturn(new URL("https://fake-s3.amazonaws.com/file.parquet"));
    when(s3Presigner.presignGetObject(any(software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest.class)))
        .thenReturn(mockPresignedReq);
  }

  @Test
  @DisplayName("Should return CalculationResponse if R responds 200 OK")
  void shouldReturnResponseOn200() {
    // GIVEN
    mockServer.expect(ExpectedCount.once(), requestTo("http://fake-plumber/calculate-ri"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withSuccess("""
                {
                  "lab_result": {
                    "test_code": "TSH",
                    "name": "RefineR Analysis",
                    "value": 2.5,
                    "unit": "mIU/L",
                    "reference_range": "0.4 - 4.0",
                    "notes": "Calculation successful"
                  },
                  "parameters": {
                    "p_low": 0.025,
                    "p_high": 0.975,
                    "n_samples": 1500
                  }
                }
                """, MediaType.APPLICATION_JSON));

    CalculationRequest request = new CalculationRequest("3.Gold/TSH/data.parquet", 0.025, 0.975, "TSH");

    // WHEN
    CalculationResponse response = plumberAdapter.calculate(request);

    // THEN
    assertThat(response.labResult().referenceRange()).isEqualTo("0.4 - 4.0");
    mockServer.verify();
  }

  @Test
  @DisplayName("Should throw DataInconsistencyException and NOT retry if R responds 422")
  void shouldFailFastOn422() {
    // GIVEN
    mockServer.expect(ExpectedCount.once(), requestTo("http://fake-plumber/calculate-ri"))
        .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY)
            .body("{\"error\": \"Missing column analyte_value\"}"));

    CalculationRequest request = new CalculationRequest("3.Gold/BAD/data.parquet", null, null, "BAD");

    // WHEN & THEN
    assertThatThrownBy(() -> plumberAdapter.calculate(request))
        .isInstanceOf(PlumberAdapter.DataInconsistencyException.class)
        .hasMessage("Missing column analyte_value");

    mockServer.verify();
  }

  @Test
  @DisplayName("Should retry 3 times and throw EngineUnavailableException if R responds 500")
  void shouldRetryOn500() {
    // GIVEN
    mockServer.expect(ExpectedCount.times(3), requestTo("http://fake-plumber/calculate-ri"))
        .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR)
            .body("{\"error\": \"Kernel panic\"}"));

    CalculationRequest request = new CalculationRequest("3.Gold/CRASH/data.parquet", null, null, "CRASH");

    // WHEN & THEN
    assertThatThrownBy(() -> plumberAdapter.calculate(request))
        .isInstanceOf(PlumberAdapter.EngineUnavailableException.class)
        .hasMessageContaining("R Error (500 INTERNAL_SERVER_ERROR)");

    mockServer.verify();
  }
}