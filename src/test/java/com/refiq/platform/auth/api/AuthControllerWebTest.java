package com.refiq.platform.auth.api;

import com.refiq.platform.auth.api.web.AuthController;
import com.refiq.platform.support.slices.BaseWebWithAuthTest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.refiq.platform.auth.api.dto.login.LoginRequest;
import com.refiq.platform.auth.api.dto.login.LoginResult;
import com.refiq.platform.auth.api.dto.registration.RegisterUserRequest;
import com.refiq.platform.auth.api.dto.registration.RegistrationResponse;
import com.refiq.platform.auth.api.dto.registration.RegistrationResult;
import com.refiq.platform.auth.internal.service.AuthService;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@WebMvcTest(AuthController.class)
@DisplayName("Auth - Web Layer (Isolated)")
class AuthControllerWebTest extends BaseWebWithAuthTest {

  @MockitoBean
  private AuthService authService;

  @Test
  @DisplayName("Successful registration: Returns 200 OK and maps RegistrationResult.Success")
  void shouldReturn200OnSuccessfulRegistration() throws Exception {
    // GIVEN
    RegisterUserRequest request = new RegisterUserRequest("Lab Central", "test@refiq.com", "SuperPassword123!");

    when(authService.register(any(RegisterUserRequest.class), anyString()))
        .thenReturn(new RegistrationResult.Success(
            new RegistrationResponse("User successfully registered", UUID.randomUUID().toString())
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
  @DisplayName("Failed registration: Returns 409 Conflict if email exists (RegistrationResult.EmailAlreadyExists)")
  void shouldReturn409WhenEmailAlreadyExists() throws Exception {
    // GIVEN
    RegisterUserRequest request = new RegisterUserRequest("Duplicate Lab", "dup@refiq.com", "SuperPassword123!");

    when(authService.register(any(RegisterUserRequest.class), anyString()))
        .thenReturn(new RegistrationResult.EmailAlreadyExists("dup@refiq.com"));

    // WHEN & THEN
    mockMvc.perform(post("/api/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.error").value("The email dup@refiq.com is already registered."));
  }

  @Test
  @DisplayName("Failed login: Returns 401 Unauthorized for invalid credentials (LoginResult.InvalidCredentials)")
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
  @DisplayName("Failed validation: Returns 400 Bad Request if password is weak")
  void shouldReturn400OnWeakPassword() throws Exception {
    // GIVEN
    RegisterUserRequest weakRequest = new RegisterUserRequest("Lab", "test@refiq.com", "weakpass123");

    // WHEN & THEN
    mockMvc.perform(post("/api/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(weakRequest)))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("Failed registration: Returns 429 Too Many Requests if service blocks (TooManyRequests)")
  void shouldReturn429WhenRegisterRateLimitExceeded() throws Exception {
    // GIVEN
    RegisterUserRequest request = new RegisterUserRequest("Spam", "spam@refiq.com", "SuperPassword123!");

    when(authService.register(any(RegisterUserRequest.class), anyString()))
        .thenReturn(new RegistrationResult.TooManyRequests("Too many registration attempts."));

    // WHEN & THEN
    mockMvc.perform(post("/api/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.error.error").exists());
  }

  @Test
  @DisplayName("Failed login: Returns 429 Too Many Requests if service blocks due to brute force (TooManyRequests)")
  void shouldReturn429WhenLoginRateLimitExceeded() throws Exception {
    // GIVEN
    LoginRequest request = new LoginRequest("hacker@refiq.com", "WrongPassword!");

    when(authService.login(any(LoginRequest.class)))
        .thenReturn(new LoginResult.TooManyRequests("Too many failed attempts."));

    // WHEN & THEN
    mockMvc.perform(post("/api/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.error.error").exists());
  }
}