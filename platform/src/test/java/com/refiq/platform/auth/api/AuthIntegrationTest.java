package com.refiq.platform.auth.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.refiq.platform.auth.api.dto.LoginRequest;
import com.refiq.platform.auth.api.dto.RegisterUserRequest;
import com.refiq.platform.auth.internal.domain.Credential;
import com.refiq.platform.auth.internal.repository.CredentialRepository;
import com.refiq.platform.user.internal.domain.UserProfile;
import com.refiq.platform.user.internal.repository.UserProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

@AutoConfigureMockMvc
@ActiveProfiles({"test", "security"})
@DisplayName("Auth - Integration API (End-to-End)")
class AuthIntegrationTest extends AuthBaseIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private CredentialRepository credentialRepository;

  @Autowired
  private UserProfileRepository userProfileRepository;

  @Autowired
  private PasswordEncoder passwordEncoder;

  @BeforeEach
  void setUp() {
    userProfileRepository.deleteAll();
    credentialRepository.deleteAll();
  }

  @Test
  @DisplayName("Registro exitoso: 200 OK y persistencia en Postgres (Auth + User)")
  void shouldRegisterUserSuccessfully() throws Exception {
    RegisterUserRequest request = new RegisterUserRequest("Laboratorios Central", "nuevo@refiq.com", "SuperPassword123!");

    mockMvc.perform(post("/api/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        // Añadido .data al path
        .andExpect(jsonPath("$.data.message").exists())
        .andExpect(jsonPath("$.data.userId").exists());

    Optional<Credential> savedCredential = credentialRepository.findByEmail("nuevo@refiq.com");
    assertThat(savedCredential).isPresent();
    assertThat(passwordEncoder.matches("SuperPassword123!", savedCredential.get().getPasswordHash())).isTrue();

    Optional<UserProfile> savedProfile = userProfileRepository.findById(savedCredential.get().getId());
    assertThat(savedProfile).isPresent();
    assertThat(savedProfile.get().getName()).isEqualTo("Laboratorios Central");
    assertThat(savedProfile.get().getContactEmail()).isEqualTo("nuevo@refiq.com");
  }

  @Test
  @DisplayName("Registro fallido: 409 Conflict si el email ya existe")
  void shouldReturn409WhenEmailAlreadyExists() throws Exception {
    RegisterUserRequest request = new RegisterUserRequest("Duplicado Labs", "duplicado@refiq.com", "SuperPassword123!");

    mockMvc.perform(post("/api/register")
            .header("X-Forwarded-For", "192.168.1.1")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk());

    // Segundo registro (Intento de duplicado): IP terminada en .2
    mockMvc.perform(post("/api/register")
            .header("X-Forwarded-For", "192.168.1.2")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isConflict())
        // Añadido .error al path
        .andExpect(jsonPath("$.error.error").value("El email duplicado@refiq.com ya está registrado."));
  }

  @Test
  @DisplayName("Login exitoso: 200 OK y devuelve JWT válido")
  void shouldLoginSuccessfully() throws Exception {
    RegisterUserRequest registerReq = new RegisterUserRequest("Login Labs", "login@refiq.com", "SuperPassword123!");

    mockMvc.perform(post("/api/register")
            .header("X-Forwarded-For", "192.168.1.3")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(registerReq)))
        .andExpect(status().isOk());

    LoginRequest loginReq = new LoginRequest("login@refiq.com", "SuperPassword123!");

    mockMvc.perform(post("/api/login")
            .header("X-Forwarded-For", "192.168.1.4")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(loginReq)))
        .andExpect(status().isOk())
        // Añadido .data al path
        .andExpect(jsonPath("$.data.token").exists())
        .andExpect(jsonPath("$.data.type").value("Bearer"));
  }

  @Test
  @DisplayName("Login fallido: 401 Unauthorized con contraseña incorrecta")
  void shouldReturn401OnBadCredentials() throws Exception {
    RegisterUserRequest registerReq = new RegisterUserRequest("Seguro Labs", "seguro@refiq.com", "RealPassword123!");

    mockMvc.perform(post("/api/register")
            .header("X-Forwarded-For", "192.168.1.5")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(registerReq)))
        .andExpect(status().isOk());

    LoginRequest loginReq = new LoginRequest("seguro@refiq.com", "FakePassword123!");

    mockMvc.perform(post("/api/login")
            .header("X-Forwarded-For", "192.168.1.6")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(loginReq)))
        .andExpect(status().isUnauthorized())
        // Añadido .error al path
        .andExpect(jsonPath("$.error.error").exists());
  }
}