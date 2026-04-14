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
import com.refiq.platform.user.internal.repository.UserProfileRepository; // Importamos el repo de User
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
  private UserProfileRepository userProfileRepository; // Inyectamos el repo para verificar el E2E completo

  @Autowired
  private PasswordEncoder passwordEncoder;

  @BeforeEach
  void setUp() {
    // Borramos ambos repos para aislar tests
    userProfileRepository.deleteAll();
    credentialRepository.deleteAll();
  }

  @Test
  @DisplayName("Registro exitoso: 200 OK y persistencia en Postgres (Auth + User)")
  void shouldRegisterUserSuccessfully() throws Exception {
    // GIVEN - Añadimos el nuevo parámetro "userName"
    RegisterUserRequest request = new RegisterUserRequest("Laboratorios Central", "nuevo@refiq.com", "SuperPassword123!");

    // WHEN & THEN (API)
    mockMvc.perform(post("/api/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").exists())
        .andExpect(jsonPath("$.userId").exists());

    // THEN (Base de Datos Auth)
    Optional<Credential> savedCredential = credentialRepository.findByEmail("nuevo@refiq.com");
    assertThat(savedCredential).isPresent();
    assertThat(passwordEncoder.matches("SuperPassword123!", savedCredential.get().getPasswordHash())).isTrue();

    // THEN (Base de Datos User - ¡Validamos el evento asíncrono!)
    Optional<UserProfile> savedProfile = userProfileRepository.findById(savedCredential.get().getId());
    assertThat(savedProfile).isPresent();
    assertThat(savedProfile.get().getName()).isEqualTo("Laboratorios Central");
    assertThat(savedProfile.get().getContactEmail()).isEqualTo("nuevo@refiq.com");
  }

  @Test
  @DisplayName("Registro fallido: 409 Conflict si el email ya existe")
  void shouldReturn409WhenEmailAlreadyExists() throws Exception {
    // GIVEN
    RegisterUserRequest request = new RegisterUserRequest("Duplicado Labs", "duplicado@refiq.com", "SuperPassword123!");

    mockMvc.perform(post("/api/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk());

    // WHEN & THEN
    mockMvc.perform(post("/api/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error").value("El email duplicado@refiq.com ya está registrado."));
  }

  @Test
  @DisplayName("Login exitoso: 200 OK y devuelve JWT válido")
  void shouldLoginSuccessfully() throws Exception {
    // GIVEN
    RegisterUserRequest registerReq = new RegisterUserRequest("Login Labs", "login@refiq.com", "SuperPassword123!");
    mockMvc.perform(post("/api/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(registerReq)))
        .andExpect(status().isOk());

    // WHEN & THEN
    LoginRequest loginReq = new LoginRequest("login@refiq.com", "SuperPassword123!");

    mockMvc.perform(post("/api/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(loginReq)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").exists())
        .andExpect(jsonPath("$.type").value("Bearer"));
  }

  @Test
  @DisplayName("Login fallido: 401 Unauthorized con contraseña incorrecta")
  void shouldReturn401OnBadCredentials() throws Exception {
    // GIVEN
    RegisterUserRequest registerReq = new RegisterUserRequest("Seguro Labs", "seguro@refiq.com", "RealPassword123!");
    mockMvc.perform(post("/api/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(registerReq)))
        .andExpect(status().isOk());

    // WHEN & THEN
    LoginRequest loginReq = new LoginRequest("seguro@refiq.com", "FakePassword123!");

    mockMvc.perform(post("/api/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(loginReq)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").exists());
  }
}