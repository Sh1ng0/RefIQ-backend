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
  @DisplayName("Debe manejar MaxUploadSizeExceededException devolviendo 417 Expectation Failed")
  void shouldHandleMaxUploadSizeExceeded() throws Exception {
    mockMvc.perform(get("/dummy/max-size"))
        .andExpect(status().isExpectationFailed()) // HttpStatus.EXPECTATION_FAILED
        .andExpect(jsonPath("$.error.error").value("El archivo excede el tamaño máximo permitido")); // Estructura de ApiError
  }

  @Test
  @DisplayName("Debe manejar HttpMessageNotReadableException devolviendo 400 Bad Request")
  void shouldHandleMalformedJson() throws Exception {
    mockMvc.perform(get("/dummy/malformed-json"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.error").value("El cuerpo de la petición (JSON) es inválido o falta.")); //[cite: 11]
  }

  @Test
  @DisplayName("Debe manejar UncheckedIOException devolviendo 500 Internal Server Error")
  void shouldHandleUncheckedIoException() throws Exception {
    mockMvc.perform(get("/dummy/io-error"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.error.error").value("Error interno del servidor al procesar el archivo.")); //[cite: 11]
  }

  @Test
  @DisplayName("Debe manejar Exception genérica devolviendo 500 Internal Server Error")
  void shouldHandleGenericException() throws Exception {
    mockMvc.perform(get("/dummy/generic-error"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.error.error").value("Error inesperado en el servidor.")); //[cite: 11]
  }

  @Test
  @DisplayName("Debe manejar MethodArgumentNotValidException devolviendo 400 y detalles de validación")
  void shouldHandleValidationErrors() throws Exception {

    mockMvc.perform(post("/dummy/validate")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.error").value("Error de validación en los datos enviados")) //[cite: 11]
        .andExpect(jsonPath("$.error.details.name").value("El nombre es obligatorio")); // Extrae el defaultMessage[cite: 11]
  }

  // -------------------------------------------------------------------------
  // DUMMY CONTROLLER PARA FORZAR ERRORES
  // -------------------------------------------------------------------------

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
      throw new Exception("Algo catastrófico ocurrió");
    }

    @PostMapping("/dummy/validate")
    public void throwValidationError(@Valid @RequestBody DummyDto dto) {

    }
  }


  record DummyDto(
      @NotBlank(message = "El nombre es obligatorio")
      String name
  ) {}
}