package com.refiq.platform.modules.calculation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.refiq.platform.calculation.api.dto.CalculationRequest;
import com.refiq.platform.calculation.api.dto.CalculationResponse;
import com.refiq.platform.calculation.api.dto.CalculationResult;
import com.refiq.platform.calculation.internal.service.CalculationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;

import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;


import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
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
    var mockResponse = new CalculationResponse(
        new CalculationResponse.LabResult("GLU", "Glucose", 95.5, "mg/dL", "70-100", "OK"),
        null
    );
    when(calculationService.runAnalysis(any()))
        .thenReturn(new CalculationResult.Success(mockResponse));

    CalculationRequest request = new CalculationRequest("s3://key", 0.025, 0.975, null);

    // WHEN & THEN
    mockMvc.perform(post("/api/v1/calculations/run")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
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
        .andExpect(jsonPath("$.debug_info").value(errorMsg));
  }


  @Test
  @DisplayName("422 Unprocessable Entity: Should handle data inconsistency (Business rejection)")
  void shouldReturn422WhenDataIsInconsistent() throws Exception {
    // GIVEN
    String reason = "Datos insuficientes para RefineR. Válidos encontrados: 5";

    when(calculationService.runAnalysis(any()))
        .thenReturn(new CalculationResult.DataInconsistency(reason));

    CalculationRequest request = new CalculationRequest("s3://bucket/pocos_datos.csv", 0.025, 0.975, null);

    // WHEN & THEN
    mockMvc.perform(post("/api/v1/calculations/run")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isUnprocessableEntity()) // Valida el status 422
        .andExpect(jsonPath("$.error").value("Data Inconsistency"))
        .andExpect(jsonPath("$.details").value(reason));
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
        .andExpect(jsonPath("$.reason").value(reason));
  }
}