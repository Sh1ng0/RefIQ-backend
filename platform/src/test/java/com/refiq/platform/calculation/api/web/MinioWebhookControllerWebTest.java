package com.refiq.platform.calculation.api.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.google.common.util.concurrent.MoreExecutors;
import com.refiq.platform.calculation.api.dto.CalculationRequest;
import com.refiq.platform.calculation.api.dto.CalculationResponse;
import com.refiq.platform.calculation.api.dto.CalculationResult;
import com.refiq.platform.calculation.internal.service.CalculationService;
import com.refiq.platform.support.slices.BaseWebWithApiKeyTest;
import java.util.List;
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

  /**
   * Reemplazamos el ExecutorService asíncrono por un MOCK inteligente.
   * Interceptamos el submit() y forzamos a que el Runnable se ejecute
   * instantáneamente en el mismo hilo del test.
   */
  @TestConfiguration
  static class SyncExecutorConfig {
    @Bean(name = "webhookExecutor")
    public java.util.concurrent.ExecutorService webhookExecutor() {
      java.util.concurrent.ExecutorService mockExecutor =
          org.mockito.Mockito.mock(java.util.concurrent.ExecutorService.class);

      org.mockito.Mockito.doAnswer(invocation -> {
        // Extraemos la tarea que el controlador intentó mandar a background
        Runnable task = invocation.getArgument(0);
        // La ejecutamos en el acto
        task.run();
        // Devolvemos un Future completado para cumplir el contrato del submit()
        return java.util.concurrent.CompletableFuture.completedFuture(null);
      }).when(mockExecutor).submit(org.mockito.ArgumentMatchers.any(Runnable.class));

      return mockExecutor;
    }
  }

  @Test
  @DisplayName("Debe devolver 403 Forbidden si falta la API Key")
  void shouldReturn403WhenApiKeyIsMissing() throws Exception {
    // WHEN & THEN
    mockMvc.perform(post("/api/v1/webhooks/minio")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("Debe devolver 403 Forbidden si la API Key es inválida")
  void shouldReturn403WhenApiKeyIsInvalid() throws Exception {
    // WHEN & THEN
    mockMvc.perform(post("/api/v1/webhooks/minio")
            .header(WEBHOOK_TOKEN_HEADER, "llave-pirata")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("Debe procesar el payload Gold y devolver 200 OK con API Key válida")
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

    // Simulamos una respuesta de éxito del servicio
    CalculationResponse.LabResult labResult = new CalculationResponse.LabResult(
        "TSH", "Hormona", 2.5, "mIU/L", "0.4-4.0", null
    );
    when(calculationService.runAnalysis(any(CalculationRequest.class)))
        .thenReturn(new CalculationResult.Success(new CalculationResponse(labResult, Map.of())));

    // WHEN & THEN
    mockMvc.perform(post("/api/v1/webhooks/minio")
            .header(WEBHOOK_TOKEN_HEADER, VALID_API_KEY)
            .contentType(MediaType.APPLICATION_JSON)
            .content(payload))
        .andExpect(status().isOk());

    // Verificamos que el controlador parseó bien la clave "TSH" y llamó al servicio
    verify(calculationService).runAnalysis(
        org.mockito.ArgumentMatchers.argThat(req ->
            req.s3Key().equals("3.Gold/TSH/TSH_data.parquet") &&
                req.testCode().equals("TSH")
        )
    );
  }

  @Test
  @DisplayName("Debe ignorar archivos que no estén en la capa Gold y devolver 200 OK")
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

    // Verificamos que NUNCA llamó al servicio porque el if filtró la ruta
    verify(calculationService, never()).runAnalysis(any());
  }
}