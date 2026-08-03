package com.refiq.platform.calculation.api.web;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.refiq.platform.calculation.internal.repository.CalculationResultRepository;
import com.refiq.platform.calculation.internal.repository.entity.CalculationResultEntity;
import com.refiq.platform.calculation.internal.repository.entity.CalculationStatus;
import com.refiq.platform.support.slices.BaseWebWithAuthTest;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@WebMvcTest(ResultController.class)
@DisplayName("Calculation - Result Web Layer (Isolated)")
class ResultControllerWebTest extends BaseWebWithAuthTest {


  @MockitoBean
  private CalculationResultRepository repository;

  @Test
  @WithMockUser
  @DisplayName("Debe devolver 202 ACCEPTED si el cálculo está PENDING")
  void shouldReturn202WhenPending() throws Exception {
    // GIVEN
    UUID fileId = UUID.randomUUID();
    CalculationResultEntity entity = CalculationResultEntity.builder()
        .id(fileId)
        .status(CalculationStatus.PENDING)
        .build();

    when(repository.findById(fileId)).thenReturn(Optional.of(entity));

    // WHEN & THEN
    mockMvc.perform(get("/api/v1/results/{fileId}", fileId))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.data.message").exists());
  }

  @Test
  @WithMockUser
  @DisplayName("Debe devolver 200 OK y el JSON si el cálculo está SUCCESS")
  void shouldReturn200WhenSuccess() throws Exception {
    // GIVEN
    UUID fileId = UUID.randomUUID();
    String jsonPayload = """
            {"test_code": "TSH", "reference_range": "0.5-4.0"}
            """;

    CalculationResultEntity entity = CalculationResultEntity.builder()
        .id(fileId)
        .status(CalculationStatus.SUCCESS)
        .payload(jsonPayload)
        .build();

    when(repository.findById(fileId)).thenReturn(Optional.of(entity));

    // WHEN & THEN
    mockMvc.perform(get("/api/v1/results/{fileId}", fileId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").exists());
  }

  @Test
  @WithMockUser
  @DisplayName("Debe devolver 500 INTERNAL SERVER ERROR si el cálculo FAILED")
  void shouldReturn500WhenFailed() throws Exception {
    // GIVEN
    UUID fileId = UUID.randomUUID();
    CalculationResultEntity entity = CalculationResultEntity.builder()
        .id(fileId)
        .status(CalculationStatus.FAILED)
        .errorMessage("Timeout en motor R")
        .build();

    when(repository.findById(fileId)).thenReturn(Optional.of(entity));

    // WHEN & THEN
    mockMvc.perform(get("/api/v1/results/{fileId}", fileId))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.error.error").value("Error en el cálculo"));
  }

  @Test
  @WithMockUser
  @DisplayName("Debe devolver 404 NOT FOUND si el UUID no existe")
  void shouldReturn404WhenNotFound() throws Exception {
    // GIVEN
    UUID unknownId = UUID.randomUUID();
    when(repository.findById(unknownId)).thenReturn(Optional.empty());

    // WHEN & THEN
    mockMvc.perform(get("/api/v1/results/{fileId}", unknownId))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.error").exists());
  }
}