package com.refiq.platform.calculation.api.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.refiq.platform.calculation.api.dto.CalculationRequest;
import com.refiq.platform.calculation.api.dto.CalculationResponse;
import com.refiq.platform.calculation.api.dto.CalculationResult;
import com.refiq.platform.calculation.internal.service.CalculationService;
import com.refiq.platform.support.slices.BaseWebWithApiKeyTest;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@WebMvcTest(MinioWebhookController.class)
@DisplayName("Calculation - MinIO Webhook Web Layer (Isolated)")
class MinioWebhookControllerWebTest extends BaseWebWithApiKeyTest {

  @MockitoBean
  private CalculationService calculationService;

  @TestConfiguration
  static class SyncExecutorConfig {
    @Bean(name = "webhookExecutor")
    public java.util.concurrent.ExecutorService webhookExecutor() {
      java.util.concurrent.ExecutorService mockExecutor =
          org.mockito.Mockito.mock(java.util.concurrent.ExecutorService.class);

      org.mockito.Mockito.doAnswer(invocation -> {
        Runnable task = invocation.getArgument(0);
        task.run();
        return java.util.concurrent.CompletableFuture.completedFuture(null);
      }).when(mockExecutor).submit(org.mockito.ArgumentMatchers.any(Runnable.class));

      return mockExecutor;
    }
  }

  @Test
  @DisplayName("Should return 403 Forbidden if the API Key is missing")
  void shouldReturn403WhenApiKeyIsMissing() throws Exception {
    // WHEN & THEN
    mockMvc.perform(post("/api/v1/webhooks/minio")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("Should return 403 Forbidden if the API Key is invalid")
  void shouldReturn403WhenApiKeyIsInvalid() throws Exception {
    // WHEN & THEN
    mockMvc.perform(post("/api/v1/webhooks/minio")
            .header(WEBHOOK_TOKEN_HEADER, "invalid-key")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("Should process the Gold payload and return 200 OK with a valid API Key")
  void shouldProcessGoldPayloadAndReturn200() throws Exception {
    // GIVEN
    String payload = """
            {
              "Records": [
                {
                  "s3": {
                    "object": {
                      "key": "3.Gold/TSH/TSH_data.parquet"
                    }
                  }
                }
              ]
            }
            """;

    CalculationResponse.LabResult labResult = new CalculationResponse.LabResult(
        "TSH", "Hormone", 2.5, "mIU/L", "0.4-4.0", null
    );
    when(calculationService.runAnalysis(any(CalculationRequest.class)))
        .thenReturn(new CalculationResult.Success(new CalculationResponse(labResult, Map.of())));

    // WHEN & THEN
    mockMvc.perform(post("/api/v1/webhooks/minio")
            .header(WEBHOOK_TOKEN_HEADER, VALID_API_KEY)
            .contentType(MediaType.APPLICATION_JSON)
            .content(payload))
        .andExpect(status().isOk());

    verify(calculationService).runAnalysis(
        org.mockito.ArgumentMatchers.argThat(req ->
            req.s3Key().equals("3.Gold/TSH/TSH_data.parquet") &&
                req.testCode().equals("TSH")
        )
    );
  }

  @Test
  @DisplayName("Should ignore non-Gold files and return 200 OK")
  void shouldIgnoreNonGoldFiles() throws Exception {
    // GIVEN
    String payload = """
            {
              "Records": [
                {
                  "s3": {
                    "object": {
                      "key": "1.Bronze/TSH/raw_data.csv"
                    }
                  }
                }
              ]
            }
            """;

    // WHEN & THEN
    mockMvc.perform(post("/api/v1/webhooks/minio")
            .header(WEBHOOK_TOKEN_HEADER, VALID_API_KEY)
            .contentType(MediaType.APPLICATION_JSON)
            .content(payload))
        .andExpect(status().isOk());

    verify(calculationService, never()).runAnalysis(any());
  }
}