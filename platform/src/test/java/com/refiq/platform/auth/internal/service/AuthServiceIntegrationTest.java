package com.refiq.platform.auth.internal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.refiq.platform.auth.api.dto.LoginRequest;
import com.refiq.platform.auth.api.dto.LoginResult;
import com.refiq.platform.auth.api.dto.RegisterUserRequest;
import com.refiq.platform.auth.api.dto.RegistrationResult;
import com.refiq.platform.auth.internal.domain.Credential;
import com.refiq.platform.auth.internal.repository.CredentialRepository;
import com.refiq.platform.auth.internal.security.AuthRateLimiter;
import com.refiq.platform.auth.internal.security.JwtProvider;
import com.refiq.platform.support.slices.BasePostgresTest;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@ApplicationModuleTest
// TODO MOdificar esto cuando se borre la puerta lógica de perfiles en la clase base
@ActiveProfiles({"test", "security"})
@DisplayName("Auth - Service Layer (Postgres Integration)")
class AuthServiceIntegrationTest extends BasePostgresTest {

  @Autowired
  private AuthService authService;

  @Autowired
  private CredentialRepository credentialRepository;

  @Autowired
  private PasswordEncoder passwordEncoder;

  // Neutralizamos el Rate Limiter para no tener "flaky tests" por culpa de la memoria
  @MockitoBean
  private AuthRateLimiter authRateLimiter;

  // Mockeamos la generación de JWT, eso lo probaremos en su test unitario correspondiente
  @MockitoBean
  private JwtProvider jwtProvider;

  @BeforeEach
  void setUp() {
    // Por defecto, permitimos todo el tráfico en el Rate Limiter
    when(authRateLimiter.tryConsumeRegister(anyString())).thenReturn(true);
    when(authRateLimiter.tryConsumeLogin(anyString())).thenReturn(true);
  }

  @Test
  @DisplayName("Debe registrar un usuario y persistir las credenciales encriptadas")
  void shouldRegisterUserAndPersistCredentials() {
    // GIVEN
    RegisterUserRequest request = new RegisterUserRequest("Lab Data", "dbtest@refiq.com", "Password123!");
    String ipAddress = "192.168.1.100";

    // WHEN
    RegistrationResult result = authService.register(request, ipAddress);

    // THEN
    assertThat(result).isInstanceOf(RegistrationResult.Success.class);

    // Verificamos el impacto real en Postgres
    Optional<Credential> savedCredential = credentialRepository.findByEmail("dbtest@refiq.com");
    assertThat(savedCredential).isPresent();
    assertThat(passwordEncoder.matches("Password123!", savedCredential.get().getPasswordHash())).isTrue();
  }

  @Test
  @DisplayName("Debe devolver EmailAlreadyExists si el correo ya está en base de datos")
  void shouldReturnEmailAlreadyExists() {
    // GIVEN
    // Inyectamos el usuario directo en DB
    Credential existingUser = Credential.builder()
        .email("duplicado@refiq.com")
        .passwordHash(passwordEncoder.encode("oldPassword!"))
        .build();
    credentialRepository.save(existingUser);

    RegisterUserRequest request = new RegisterUserRequest("Lab Nuevo", "duplicado@refiq.com", "newPassword!");

    // WHEN
    RegistrationResult result = authService.register(request, "127.0.0.1");

    // THEN
    assertThat(result).isInstanceOf(RegistrationResult.EmailAlreadyExists.class);
  }

  @Test
  @DisplayName("Debe bloquear el registro si el Rate Limiter lo indica")
  void shouldBlockRegistrationOnRateLimitExceeded() {
    // GIVEN
    // Forzamos al mock a simular un ataque de fuerza bruta
    when(authRateLimiter.tryConsumeRegister("10.0.0.1")).thenReturn(false);
    RegisterUserRequest request = new RegisterUserRequest("Spam", "spam@refiq.com", "Pass123!");

    // WHEN
    RegistrationResult result = authService.register(request, "10.0.0.1");

    // THEN
    assertThat(result).isInstanceOf(RegistrationResult.TooManyRequests.class);
    // Verificamos que no se ha tocado la DB
    assertThat(credentialRepository.findByEmail("spam@refiq.com")).isEmpty();
  }

  @Test
  @DisplayName("Debe autenticar correctamente con credenciales válidas")
  void shouldLoginSuccessfully() {
    // GIVEN
    Credential user = Credential.builder()
        .email("login@refiq.com")
        .passwordHash(passwordEncoder.encode("CorrectPass123!"))
        .build();
    credentialRepository.save(user);

    when(jwtProvider.generateToken(any(UUID.class))).thenReturn("mocked.jwt.token");

    LoginRequest request = new LoginRequest("login@refiq.com", "CorrectPass123!");

    // WHEN
    LoginResult result = authService.login(request);

    // THEN
    assertThat(result).isInstanceOf(LoginResult.Success.class);
    LoginResult.Success successResult = (LoginResult.Success) result;
    assertThat(successResult.response().token()).isEqualTo("mocked.jwt.token");
  }

  @Test
  @DisplayName("Debe devolver InvalidCredentials si la contraseña no coincide")
  void shouldReturnInvalidCredentialsOnWrongPassword() {
    // GIVEN
    Credential user = Credential.builder()
        .email("secure@refiq.com")
        .passwordHash(passwordEncoder.encode("RealPassword!"))
        .build();
    credentialRepository.save(user);

    LoginRequest request = new LoginRequest("secure@refiq.com", "WrongPassword!");

    // WHEN
    LoginResult result = authService.login(request);

    // THEN
    assertThat(result).isInstanceOf(LoginResult.InvalidCredentials.class);
  }
}