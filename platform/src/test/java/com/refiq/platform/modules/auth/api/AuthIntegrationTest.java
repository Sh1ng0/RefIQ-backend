package com.refiq.platform.modules.auth.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.refiq.platform.auth.api.dto.RegisterUserRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @Test
  @DisplayName("Should register a new user successfully (200 OK)")
  void shouldRegisterNewUser() throws Exception {
    var request = new RegisterUserRequest("test@refiq.com", "SecurePass123!");

    mockMvc.perform(post("/api/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").value("Usuario registrado correctamente"))
        .andExpect(jsonPath("$.userId").isNotEmpty());
  }

  @Test
  @DisplayName("Should fail when email already exists (409 Conflict)")
  void shouldFailIfEmailExists() throws Exception {

    var request = new RegisterUserRequest("duplicado@refiq.com", "Pass12345!");

    mockMvc.perform(post("/api/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk());

    mockMvc.perform(post("/api/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isConflict()) // 409
        .andExpect(jsonPath("$.error").value("El email duplicado@refiq.com ya está registrado."));
  }

  @Test
  @DisplayName("Should fail validation with weak password (400 Bad Request)")
  void shouldFailValidation() throws Exception {

    var request = new RegisterUserRequest("mal@refiq.com", "123");

    mockMvc.perform(post("/api/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());

  }

  @Test
  @DisplayName("Should fail validation with weak password (complexity check)")
  void shouldFailWeakPassword() throws Exception {

    var request = new RegisterUserRequest("weak@refiq.com", "SoloLetrasYNumeros1");

    mockMvc.perform(post("/api/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());
  }
}