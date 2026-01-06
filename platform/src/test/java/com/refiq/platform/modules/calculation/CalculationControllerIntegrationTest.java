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

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@DisplayName("📊 Cálculo - Integración de API (Controller)")
class CalculationControllerIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @MockitoBean
  private CalculationService calculationService;

  @Test
  @DisplayName("Debe devolver 200 OK cuando el cálculo es exitoso")
  void shouldReturn200WhenSuccess() throws Exception {
    // Preparar el mock del servicio
    var mockResponse = new CalculationResponse(
        new CalculationResponse.LabResult("GLU", "Glucose", null, "mg/dL", "70-100", "OK"),
        null
    );
    when(calculationService.runAnalysis(any())).thenReturn(new CalculationResult.Success(mockResponse));

    // Ejecutar la petición
    CalculationRequest request = new CalculationRequest("s3://key", 0.025, 0.975);

    mockMvc.perform(post("/api/v1/calculations/run")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.lab_result.reference_range").value("70-100"));
  }

  @Test
  @DisplayName("Debe devolver 503 Service Unavailable cuando el motor de R falla")
  void shouldReturn503WhenEngineFails() throws Exception {
    // Simulamos fallo técnico en el servicio
    when(calculationService.runAnalysis(any()))
        .thenReturn(new CalculationResult.EngineUnavailable("Connection Timeout in R-Plumber"));

    CalculationRequest request = new CalculationRequest("s3://key", 0.05, 0.95);

    mockMvc.perform(post("/api/v1/calculations/run")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isServiceUnavailable());
  }
}