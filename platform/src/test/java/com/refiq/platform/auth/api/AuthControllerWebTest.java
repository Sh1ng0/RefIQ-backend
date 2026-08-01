package com.refiq.platform.auth.api;

import com.refiq.platform.auth.api.web.AuthController;
import com.refiq.platform.support.slices.BaseWebWithAuthTest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.refiq.platform.auth.api.dto.LoginRequest;
import com.refiq.platform.auth.api.dto.LoginResponse;
import com.refiq.platform.auth.api.dto.LoginResult;
import com.refiq.platform.auth.api.dto.RegisterUserRequest;
import com.refiq.platform.auth.api.dto.RegistrationResponse;
import com.refiq.platform.auth.api.dto.RegistrationResult;
import com.refiq.platform.auth.internal.service.AuthService;
import com.refiq.platform.support.slices.BaseWebWithAuthTest;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@WebMvcTest(AuthController.class)
@DisplayName("Auth - Web Layer (Isolated)")
class AuthControllerWebTest extends BaseWebWithAuthTest {

  // Mockeamos el servicio para controlar el flujo DOP (Data-Oriented Programming)
  @MockitoBean
  private AuthService authService;

  @Test
  @DisplayName("Registro exitoso: Devuelve 200 OK y mapea RegistrationResult.Success")
  void shouldReturn200OnSuccessfulRegistration() throws Exception {
    // GIVEN
    RegisterUserRequest request = new RegisterUserRequest("Lab Central", "test@refiq.com", "SuperPassword123!");

    // Simulamos la respuesta sellada de tu servicio
    when(authService.register(any(RegisterUserRequest.class), anyString()))
        .thenReturn(new RegistrationResult.Success(
            new RegistrationResponse("Usuario registrado correctamente", UUID.randomUUID().toString())
        ));

    // WHEN & THEN
    mockMvc.perform(post("/api/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.message").exists())
        .andExpect(jsonPath("$.data.userId").exists());
  }

  @Test
  @DisplayName("Registro fallido: Devuelve 409 Conflict si el email existe (RegistrationResult.EmailAlreadyExists)")
  void shouldReturn409WhenEmailAlreadyExists() throws Exception {
    // GIVEN
    RegisterUserRequest request = new RegisterUserRequest("Lab Duplicado", "dup@refiq.com", "SuperPassword123!");

    // El servicio nos dice que hay conflicto usando la interfaz sellada
    when(authService.register(any(RegisterUserRequest.class), anyString()))
        .thenReturn(new RegistrationResult.EmailAlreadyExists("dup@refiq.com"));

    // WHEN & THEN
    mockMvc.perform(post("/api/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.error").value("El email dup@refiq.com ya está registrado."));
  }

  @Test
  @DisplayName("Login fallido: Devuelve 401 Unauthorized para credenciales inválidas (LoginResult.InvalidCredentials)")
  void shouldReturn401OnBadCredentials() throws Exception {
    // GIVEN
    LoginRequest request = new LoginRequest("test@refiq.com", "WrongPassword!");

    when(authService.login(any(LoginRequest.class)))
        .thenReturn(new LoginResult.InvalidCredentials());

    // WHEN & THEN
    mockMvc.perform(post("/api/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.error").exists());
  }

  @Test
  @DisplayName("Validación fallida: Devuelve 400 Bad Request si la contraseña es débil")
  void shouldReturn400OnWeakPassword() throws Exception {
    // GIVEN
    // La contraseña falla el regex (falta mayúscula y símbolo)
    RegisterUserRequest weakRequest = new RegisterUserRequest("Lab", "test@refiq.com", "weakpass123");

    // WHEN & THEN
    // Ni siquiera necesitamos mockear AuthService, Spring bloquea la petición antes de llegar al controlador
    mockMvc.perform(post("/api/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(weakRequest)))
        .andExpect(status().isBadRequest());
  }
}