package com.refiq.platform.modules.calculation;




import com.refiq.platform.calculation.api.dto.CalculationResult;
import com.refiq.platform.calculation.api.web.MinioWebhookController;
import com.refiq.platform.calculation.internal.service.CalculationService;
import com.refiq.platform.support.security.TestSecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = MinioWebhookController.class)
@Import(TestSecurityConfig.class)
@ActiveProfiles("test")
public class MinioWebHookControllerIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private CalculationService calculationService;

  @Test
  @DisplayName("200 OK & Process: Should accept Gold layer events and trigger calculation asynchronously")
  void shouldProcessGoldEvent() throws Exception {
    // GIVEN
    // Inyectamos un nombre de archivo real con su UUID
    String s3KeyConUuid = "3.Gold/TSH/TSH_123e4567-e89b-12d3-a456-426614174000.parquet";

    String minioPayload = """
        {
          "EventName": "s3:ObjectCreated:Put",
          "Records": [
            {
              "s3": {
                "object": {
                  "key": "%s"
                }
              }
            }
          ]
        }
        """.formatted(s3KeyConUuid);

    when(calculationService.runAnalysis(any()))
        .thenReturn(new CalculationResult.InvalidRequest("Dummy Result for Test"));

    // WHEN
    mockMvc.perform(post("/api/v1/webhooks/minio")
            .contentType(MediaType.APPLICATION_JSON)
            .content(minioPayload))
        .andExpect(status().isOk());

    // THEN
    verify(calculationService, timeout(1000).times(1)).runAnalysis(argThat(request ->
        request.testCode().equals("TSH") &&
            request.s3Key().equals(s3KeyConUuid) // Comparamos contra la nueva ruta
    ));
  }

  @Test
  @DisplayName("200 OK & Ignore: Should ignore Bronze layer events without triggering calculation")
  void shouldIgnoreBronzeEvent() throws Exception {
    // GIVEN
    String minioPayload = """
        {
          "EventName": "s3:ObjectCreated:Put",
          "Records": [
            {
              "s3": {
                "object": {
                  "key": "1.Bronze/ALP/ALP_1234.csv"
                }
              }
            }
          ]
        }
        """;

    // WHEN
    mockMvc.perform(post("/api/v1/webhooks/minio")
            .contentType(MediaType.APPLICATION_JSON)
            .content(minioPayload))
        // THEN - Respuesta HTTP (Para que MinIO no reintente)
        .andExpect(status().isOk());

    // THEN - Verificamos que el servicio NO fue llamado
    verify(calculationService, timeout(500).times(0)).runAnalysis(any());
  }
}