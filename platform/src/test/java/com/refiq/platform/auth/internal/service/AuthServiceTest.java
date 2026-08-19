package com.refiq.platform.auth.internal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.refiq.platform.auth.api.dto.LoginRequest;
import com.refiq.platform.auth.api.dto.LoginResult;
import com.refiq.platform.auth.api.dto.RegisterUserRequest;
import com.refiq.platform.auth.api.dto.RegistrationResult;
import com.refiq.platform.auth.api.event.UserRegisteredEvent;
import com.refiq.platform.auth.internal.domain.Credential;
import com.refiq.platform.auth.internal.repository.DbCredentialRepository; // <-- Import actualizado
import com.refiq.platform.auth.internal.security.AuthRateLimiter;
import com.refiq.platform.auth.internal.security.JwtProvider;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService - Unit Tests")
class AuthServiceTest {

  @Mock
  private DbCredentialRepository credentialRepository; // <-- Actualizado

  @Mock
  private PasswordEncoder passwordEncoder;

  @Mock
  private JwtProvider jwtProvider;

  @Mock
  private AuthRateLimiter authRateLimiter;

  @Mock
  private ApplicationEventPublisher eventPublisher;

  @InjectMocks
  private AuthService authService;

  private final String TEST_IP = "192.168.1.50";

  // --- TESTS DE REGISTRO ---

  @Test
  @DisplayName("Registro: Bloqueado por Rate Limiting antes de tocar la BD")
  void register_ShouldReturnTooManyRequests_WhenRateLimiterBlocks() {
    RegisterUserRequest request = new RegisterUserRequest("Spammer Org", "spammer@refiq.com", "Password123!");
    when(authRateLimiter.tryConsumeRegister(TEST_IP)).thenReturn(false);

    RegistrationResult result = authService.register(request, TEST_IP);

    assertThat(result).isInstanceOf(RegistrationResult.TooManyRequests.class);
    verify(credentialRepository, never()).existsByEmail(anyString());
    verify(eventPublisher, never()).publishEvent(any());
  }

  @Test
  @DisplayName("Registro: Falla si el email ya existe")
  void register_ShouldReturnEmailAlreadyExists_WhenEmailIsInDb() {
    RegisterUserRequest request = new RegisterUserRequest("Test Org", "test@refiq.com", "Password123!");
    when(authRateLimiter.tryConsumeRegister(TEST_IP)).thenReturn(true);
    when(credentialRepository.existsByEmail("test@refiq.com")).thenReturn(true);

    RegistrationResult result = authService.register(request, TEST_IP);

    assertThat(result).isInstanceOf(RegistrationResult.EmailAlreadyExists.class);
    verify(credentialRepository, never()).insert(any()); // <-- Actualizado a insert()
    verify(eventPublisher, never()).publishEvent(any());
  }

  @Test
  @DisplayName("Registro: Exitoso, hashea la contraseña, dispara evento y devuelve Success")
  void register_ShouldHashPasswordAndReturnSuccess() {
    RegisterUserRequest request = new RegisterUserRequest("Nuevo Org", "nuevo@refiq.com", "Password123!");

    when(authRateLimiter.tryConsumeRegister(TEST_IP)).thenReturn(true);
    when(credentialRepository.existsByEmail("nuevo@refiq.com")).thenReturn(false);
    when(passwordEncoder.encode("Password123!")).thenReturn("hashed_password");

    // Ya no mockeamos el .save() porque insert() es void y el servicio genera el ID

    RegistrationResult result = authService.register(request, TEST_IP);

    assertThat(result).isInstanceOf(RegistrationResult.Success.class);

    // Capturamos el record inmutable para validarlo
    ArgumentCaptor<Credential> credentialCaptor = ArgumentCaptor.forClass(Credential.class);
    verify(credentialRepository).insert(credentialCaptor.capture()); // <-- Actualizado
    Credential savedCredential = credentialCaptor.getValue();

    assertThat(savedCredential.passwordHash()).isEqualTo("hashed_password"); // <-- Accesor de Record

    RegistrationResult.Success success = (RegistrationResult.Success) result;
    assertThat(success.response().userId()).isEqualTo(savedCredential.id().toString()); // <-- Validamos contra el ID generado

    ArgumentCaptor<UserRegisteredEvent> eventCaptor = ArgumentCaptor.forClass(UserRegisteredEvent.class);
    verify(eventPublisher).publishEvent(eventCaptor.capture());
    assertThat(eventCaptor.getValue().accountId()).isEqualTo(savedCredential.id());
  }

  // --- TESTS DE LOGIN ---

  @Test
  @DisplayName("Login: Bloqueado por Rate Limiting antes de tocar la BD")
  void login_ShouldReturnTooManyRequests_WhenRateLimiterBlocks() {
    LoginRequest request = new LoginRequest("spammer@refiq.com", "pass");
    when(authRateLimiter.tryConsumeLogin("spammer@refiq.com")).thenReturn(false);

    LoginResult result = authService.login(request);

    assertThat(result).isInstanceOf(LoginResult.TooManyRequests.class);
    verify(credentialRepository, never()).findByEmail(anyString());
  }

  @Test
  @DisplayName("Login: Falla por credenciales inválidas (Usuario no existe)")
  void login_ShouldReturnInvalidCredentials_WhenUserNotFound() {
    LoginRequest request = new LoginRequest("fantasma@refiq.com", "pass");
    when(authRateLimiter.tryConsumeLogin("fantasma@refiq.com")).thenReturn(true);
    when(credentialRepository.findByEmail("fantasma@refiq.com")).thenReturn(Optional.empty());

    LoginResult result = authService.login(request);

    assertThat(result).isInstanceOf(LoginResult.InvalidCredentials.class);
  }

  @Test
  @DisplayName("Login: Falla por credenciales inválidas (Mala contraseña)")
  void login_ShouldReturnInvalidCredentials_WhenPasswordDoesNotMatch() {
    LoginRequest request = new LoginRequest("real@refiq.com", "bad_pass");
    // <-- Actualizado a constructor de Record
    Credential credential = new Credential(UUID.randomUUID(), "real@refiq.com", "hash_real", Instant.now());

    when(authRateLimiter.tryConsumeLogin("real@refiq.com")).thenReturn(true);
    when(credentialRepository.findByEmail("real@refiq.com")).thenReturn(Optional.of(credential));
    when(passwordEncoder.matches("bad_pass", "hash_real")).thenReturn(false);

    LoginResult result = authService.login(request);

    assertThat(result).isInstanceOf(LoginResult.InvalidCredentials.class);
    verify(jwtProvider, never()).generateToken(any());
  }

  @Test
  @DisplayName("Login: Exitoso, devuelve Success con el JWT")
  void login_ShouldReturnSuccessWithToken_WhenCredentialsAreValid() {
    UUID userId = UUID.randomUUID();
    LoginRequest request = new LoginRequest("pro@refiq.com", "good_pass");
    // <-- Actualizado a constructor de Record
    Credential credential = new Credential(userId, "pro@refiq.com", "hash_real", Instant.now());

    when(authRateLimiter.tryConsumeLogin("pro@refiq.com")).thenReturn(true);
    when(credentialRepository.findByEmail("pro@refiq.com")).thenReturn(Optional.of(credential));
    when(passwordEncoder.matches("good_pass", "hash_real")).thenReturn(true);
    when(jwtProvider.generateToken(userId)).thenReturn("mocked.jwt.token");

    LoginResult result = authService.login(request);

    assertThat(result).isInstanceOf(LoginResult.Success.class);

    LoginResult.Success success = (LoginResult.Success) result;
    assertThat(success.response().token()).isEqualTo("mocked.jwt.token");
    assertThat(success.response().type()).isEqualTo("Bearer");
  }
}