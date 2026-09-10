package com.refiq.platform.shared.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@DisplayName("Shared - Global Exception Handler (Standalone)")
class GlobalExceptionHandlerTest {

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.standaloneSetup(new DummyController())
        .setControllerAdvice(new GlobalExceptionHandler())
        .build();
  }

  @Test
  @DisplayName("Should handle MaxUploadSizeExceededException returning 417 Expectation Failed")
  void shouldHandleMaxUploadSizeExceeded() throws Exception {
    // WHEN & THEN
    mockMvc.perform(get("/dummy/max-size"))
        .andExpect(status().isExpectationFailed())
        .andExpect(jsonPath("$.error.error").value("The file exceeds the maximum allowed size."));
  }

  @Test
  @DisplayName("Should handle HttpMessageNotReadableException returning 400 Bad Request")
  void shouldHandleMalformedJson() throws Exception {
    // WHEN & THEN
    mockMvc.perform(get("/dummy/malformed-json"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.error").value("The request body (JSON) is missing or malformed."));
  }

  @Test
  @DisplayName("Should handle UncheckedIOException returning 500 Internal Server Error")
  void shouldHandleUncheckedIoException() throws Exception {
    // WHEN & THEN
    mockMvc.perform(get("/dummy/io-error"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.error.error").value("Internal server error while processing the file."));
  }

  @Test
  @DisplayName("Should handle generic Exception returning 500 Internal Server Error")
  void shouldHandleGenericException() throws Exception {
    // WHEN & THEN
    mockMvc.perform(get("/dummy/generic-error"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.error.error").value("Unexpected server error."));
  }

  @Test
  @DisplayName("Should handle MethodArgumentNotValidException returning 400 and validation details")
  void shouldHandleValidationErrors() throws Exception {
    // WHEN & THEN
    mockMvc.perform(post("/dummy/validate")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.error").value("Validation error in the provided data"))
        .andExpect(jsonPath("$.error.details.name").value("The name is mandatory"));
  }

  @RestController
  static class DummyController {

    @GetMapping("/dummy/max-size")
    public void throwMaxSize() {
      throw new MaxUploadSizeExceededException(5000);
    }

    @GetMapping("/dummy/malformed-json")
    public void throwMalformedJson() {
      throw new HttpMessageNotReadableException("JSON parse error", (HttpInputMessage) null);
    }

    @GetMapping("/dummy/io-error")
    public void throwIoError() {
      throw new UncheckedIOException("Disk full", new IOException());
    }

    @GetMapping("/dummy/generic-error")
    public void throwGeneric() throws Exception {
      throw new Exception("Something catastrophic occurred");
    }

    @PostMapping("/dummy/validate")
    public void throwValidationError(@Valid @RequestBody DummyDto dto) {
    }
  }

  record DummyDto(
      @NotBlank(message = "The name is mandatory")
      String name
  ) {}
}