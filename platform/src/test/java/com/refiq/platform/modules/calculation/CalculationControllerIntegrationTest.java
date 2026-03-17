package com.refiq.platform.modules.calculation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.refiq.platform.auth.internal.security.JwtAuthenticationFilter;
import com.refiq.platform.auth.internal.security.JwtProvider;
import com.refiq.platform.calculation.api.dto.CalculationRequest;
import com.refiq.platform.calculation.api.dto.CalculationResponse;
import com.refiq.platform.calculation.api.dto.CalculationResult;
import com.refiq.platform.calculation.api.web.CalculationController;
import com.refiq.platform.calculation.internal.service.CalculationService;
import com.refiq.platform.support.security.TestSecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


// SLICE TEST
@WebMvcTest(controllers = CalculationController.class)
@ActiveProfiles("test")
@Import(TestSecurityConfig.class)
@DisplayName("Calculation - Integration API (Controller)")
class CalculationControllerIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @MockitoBean
  private CalculationService calculationService;


  @Test
  @DisplayName("200 OK: Should return JSON result on successful calculation")
  void shouldReturn200WhenSuccess() throws Exception {
    // GIVEN
    var labResult = new CalculationResponse.LabResult(
        "GLU", "Glucose", 95.5, "mg/dL", "70-100", "OK"
    );

    var mockResponse = new CalculationResponse(labResult, null);

    when(calculationService.runAnalysis(any()))
        .thenReturn(new CalculationResult.Success(mockResponse));

    CalculationRequest request = new CalculationRequest("s3://key", 0.025, 0.975, null);

    // WHEN & THEN
    mockMvc.perform(post("/api/v1/calculations/run")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        // CORRECCIÓN
        .andExpect(jsonPath("$.lab_result.reference_range").value("70-100"))
        .andExpect(jsonPath("$.lab_result.value").value(95.5));
  }

  @Test
  @DisplayName("503 Service Unavailable: Should handle R engine technical failure")
  void shouldReturn503WhenEngineFails() throws Exception {
    // GIVEN
    String errorMsg = "Connection Timeout in R-Plumber";
    when(calculationService.runAnalysis(any()))
        .thenReturn(new CalculationResult.EngineUnavailable(errorMsg));

    CalculationRequest request = new CalculationRequest("s3://key", 0.05, 0.95, null);

    // WHEN & THEN
    mockMvc.perform(post("/api/v1/calculations/run")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.error").value("Engine Unavailable"))
        .andExpect(jsonPath("$.details.debug_info").value(errorMsg));
  }

  @Test
  @DisplayName("422 Unprocessable Entity: Should handle data inconsistency (Business rejection)")
  void shouldReturn422WhenDataIsInconsistent() throws Exception {
    // GIVEN
    String reason = "Datos insuficientes para RefineR. Válidos encontrados: 5";

    when(calculationService.runAnalysis(any()))
        .thenReturn(new CalculationResult.DataInconsistency(reason));

    CalculationRequest request = new CalculationRequest("s3://bucket/pocos_datos.csv", 0.025, 0.975,
        null);

    // WHEN & THEN
    mockMvc.perform(post("/api/v1/calculations/run")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.error").value("Data Inconsistency"))
        .andExpect(jsonPath("$.details.details").value(reason));
  }

  @Test
  @DisplayName("400 Bad Request: Should handle logically invalid requests")
  void shouldReturn400WhenRequestIsInvalid() throws Exception {
    // GIVEN
    String reason = "Percentil inválido";
    when(calculationService.runAnalysis(any()))
        .thenReturn(new CalculationResult.InvalidRequest(reason));

    CalculationRequest request = new CalculationRequest("s3://bucket/bad.csv", 0.025, 0.975, null);

    // WHEN & THEN
    mockMvc.perform(post("/api/v1/calculations/run")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("Invalid Request"))
        .andExpect(jsonPath("$.details.reason").value(reason));
  }
}