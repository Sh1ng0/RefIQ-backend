package com.refiq.platform.modules.calculation.api.web;



import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.refiq.platform.auth.internal.config.SecurityConfig; // OJO a los imports
import com.refiq.platform.auth.internal.security.DataLakeApiKeyFilter;
import com.refiq.platform.auth.internal.security.JwtAuthenticationFilter;
import com.refiq.platform.calculation.api.web.CalculationController;
import com.refiq.platform.calculation.internal.service.CalculationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;


// DEUDA TECNICA: Mejorar este test para quitar mocks innecesarios

@WebMvcTest(controllers = CalculationController.class)

@ActiveProfiles({"test", "security"})

@Import({SecurityConfig.class, DataLakeApiKeyFilter.class})
@DisplayName("Calculation Security - API Key Filter")
class CalculationSecurityTest {

  @Autowired
  private MockMvc mockMvc;


  @MockitoBean
  private CalculationService calculationService;


  @MockitoBean
  private JwtAuthenticationFilter jwtAuthenticationFilter;


  private static final String VALID_API_KEY = "test-secret-key-123";

  @Test
  @DisplayName("403 Forbidden: Sin API Key en el header")
  void shouldReturn403WhenNoApiKeyProvided() throws Exception {
    mockMvc.perform(post("/api/v1/calculations/run")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"s3Key\":\"test.csv\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("403 Forbidden: Con API Key incorrecta")
  void shouldReturn403WhenInvalidApiKeyProvided() throws Exception {
    mockMvc.perform(post("/api/v1/calculations/run")
            .header("X-RefIQ-Data-Token", "clave_falsa_del_hacker")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"s3Key\":\"test.csv\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("Pasa el filtro de seguridad con API Key correcta")
  void shouldPassSecurityFilterWhenValidApiKeyProvided() throws Exception {


    mockMvc.perform(post("/api/v1/calculations/run")
            .header("X-RefIQ-Data-Token", VALID_API_KEY)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"s3Key\":\"test.csv\"}"))
        .andExpect(status().isOk());
  }
}