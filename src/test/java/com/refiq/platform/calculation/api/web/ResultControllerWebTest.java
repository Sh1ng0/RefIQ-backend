package com.refiq.platform.calculation.api.web;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.refiq.platform.calculation.internal.domain.CalculationState;
import com.refiq.platform.calculation.internal.repository.DbCalculationResultRepository;
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
  private DbCalculationResultRepository repository;

  @Test
  @WithMockUser
  @DisplayName("Should return 202 ACCEPTED if the calculation is PENDING")
  void shouldReturn202WhenPending() throws Exception {
    // GIVEN
    UUID fileId = UUID.randomUUID();
    var pendingState = new CalculationState.Pending(fileId);

    when(repository.findById(fileId)).thenReturn(Optional.of(pendingState));

    // WHEN & THEN
    mockMvc.perform(get("/api/v1/results/{fileId}", fileId))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.data.message").exists());
  }

  @Test
  @WithMockUser
  @DisplayName("Should return 200 OK and the JSON if the calculation is SUCCESS")
  void shouldReturn200WhenSuccess() throws Exception {
    // GIVEN
    UUID fileId = UUID.randomUUID();
    String jsonPayload = """
            {"test_code": "TSH", "reference_range": "0.5-4.0"}
            """;

    var successState = new CalculationState.Success(fileId, jsonPayload);

    when(repository.findById(fileId)).thenReturn(Optional.of(successState));

    // WHEN & THEN
    mockMvc.perform(get("/api/v1/results/{fileId}", fileId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").exists());
  }

  @Test
  @WithMockUser
  @DisplayName("Should return 500 INTERNAL SERVER ERROR if the calculation FAILED")
  void shouldReturn500WhenFailed() throws Exception {
    // GIVEN
    UUID fileId = UUID.randomUUID();
    var failedState = new CalculationState.Failed(fileId, "Timeout in R engine");

    when(repository.findById(fileId)).thenReturn(Optional.of(failedState));

    // WHEN & THEN
    mockMvc.perform(get("/api/v1/results/{fileId}", fileId))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.error.error").value("Calculation error"));
  }

  @Test
  @WithMockUser
  @DisplayName("Should return 404 NOT FOUND if the UUID does not exist")
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