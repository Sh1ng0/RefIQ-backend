package com.refiq.platform.auth.internal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.refiq.platform.auth.api.dto.login.LoginRequest;
import com.refiq.platform.auth.api.dto.login.LoginResult;
import com.refiq.platform.auth.api.dto.registration.RegisterUserRequest;
import com.refiq.platform.auth.api.dto.registration.RegistrationResult;
import com.refiq.platform.auth.api.event.UserRegisteredEvent;
import com.refiq.platform.auth.internal.domain.Credential;
import com.refiq.platform.auth.internal.repository.DbCredentialRepository;
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
  private DbCredentialRepository credentialRepository;

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

  @Test
  @DisplayName("Registration: Blocked by Rate Limiting before accessing the DB")
  void register_ShouldReturnTooManyRequests_WhenRateLimiterBlocks() {
    // GIVEN
    RegisterUserRequest request = new RegisterUserRequest("Spammer Org", "spammer@refiq.com", "Password123!");
    when(authRateLimiter.tryConsumeRegister(TEST_IP)).thenReturn(false);

    // WHEN
    RegistrationResult result = authService.register(request, TEST_IP);

    // THEN
    assertThat(result).isInstanceOf(RegistrationResult.TooManyRequests.class);
    verify(credentialRepository, never()).existsByEmail(anyString());
    verify(eventPublisher, never()).publishEvent(any());
  }

  @Test
  @DisplayName("Registration: Fails if the email already exists")
  void register_ShouldReturnEmailAlreadyExists_WhenEmailIsInDb() {
    // GIVEN
    RegisterUserRequest request = new RegisterUserRequest("Test Org", "test@refiq.com", "Password123!");
    when(authRateLimiter.tryConsumeRegister(TEST_IP)).thenReturn(true);
    when(credentialRepository.existsByEmail("test@refiq.com")).thenReturn(true);

    // WHEN
    RegistrationResult result = authService.register(request, TEST_IP);

    // THEN
    assertThat(result).isInstanceOf(RegistrationResult.EmailAlreadyExists.class);
    verify(credentialRepository, never()).insert(any());
    verify(eventPublisher, never()).publishEvent(any());
  }

  @Test
  @DisplayName("Registration: Successful, hashes the password, fires event, and returns Success")
  void register_ShouldHashPasswordAndReturnSuccess() {
    // GIVEN
    RegisterUserRequest request = new RegisterUserRequest("New Org", "new@refiq.com", "Password123!");

    when(authRateLimiter.tryConsumeRegister(TEST_IP)).thenReturn(true);
    when(credentialRepository.existsByEmail("new@refiq.com")).thenReturn(false);
    when(passwordEncoder.encode("Password123!")).thenReturn("hashed_password");

    // WHEN
    RegistrationResult result = authService.register(request, TEST_IP);

    // THEN
    assertThat(result).isInstanceOf(RegistrationResult.Success.class);

    ArgumentCaptor<Credential> credentialCaptor = ArgumentCaptor.forClass(Credential.class);
    verify(credentialRepository).insert(credentialCaptor.capture());
    Credential savedCredential = credentialCaptor.getValue();

    assertThat(savedCredential.passwordHash()).isEqualTo("hashed_password");

    RegistrationResult.Success success = (RegistrationResult.Success) result;
    assertThat(success.response().userId()).isEqualTo(savedCredential.id().toString());

    ArgumentCaptor<UserRegisteredEvent> eventCaptor = ArgumentCaptor.forClass(UserRegisteredEvent.class);
    verify(eventPublisher).publishEvent(eventCaptor.capture());
    assertThat(eventCaptor.getValue().accountId()).isEqualTo(savedCredential.id());
  }

  @Test
  @DisplayName("Login: Blocked by Rate Limiting before accessing the DB")
  void login_ShouldReturnTooManyRequests_WhenRateLimiterBlocks() {
    // GIVEN
    LoginRequest request = new LoginRequest("spammer@refiq.com", "pass");
    when(authRateLimiter.tryConsumeLogin("spammer@refiq.com")).thenReturn(false);

    // WHEN
    LoginResult result = authService.login(request);

    // THEN
    assertThat(result).isInstanceOf(LoginResult.TooManyRequests.class);
    verify(credentialRepository, never()).findByEmail(anyString());
  }

  @Test
  @DisplayName("Login: Fails due to invalid credentials (User not found)")
  void login_ShouldReturnInvalidCredentials_WhenUserNotFound() {
    // GIVEN
    LoginRequest request = new LoginRequest("phantom@refiq.com", "pass");
    when(authRateLimiter.tryConsumeLogin("phantom@refiq.com")).thenReturn(true);
    when(credentialRepository.findByEmail("phantom@refiq.com")).thenReturn(Optional.empty());

    // WHEN
    LoginResult result = authService.login(request);

    // THEN
    assertThat(result).isInstanceOf(LoginResult.InvalidCredentials.class);
  }

  @Test
  @DisplayName("Login: Fails due to invalid credentials (Bad password)")
  void login_ShouldReturnInvalidCredentials_WhenPasswordDoesNotMatch() {
    // GIVEN
    LoginRequest request = new LoginRequest("real@refiq.com", "bad_pass");
    Credential credential = new Credential(UUID.randomUUID(), "real@refiq.com", "real_hash", Instant.now());

    when(authRateLimiter.tryConsumeLogin("real@refiq.com")).thenReturn(true);
    when(credentialRepository.findByEmail("real@refiq.com")).thenReturn(Optional.of(credential));
    when(passwordEncoder.matches("bad_pass", "real_hash")).thenReturn(false);

    // WHEN
    LoginResult result = authService.login(request);

    // THEN
    assertThat(result).isInstanceOf(LoginResult.InvalidCredentials.class);
    verify(jwtProvider, never()).generateToken(any());
  }

  @Test
  @DisplayName("Login: Successful, returns Success with the JWT")
  void login_ShouldReturnSuccessWithToken_WhenCredentialsAreValid() {
    // GIVEN
    UUID userId = UUID.randomUUID();
    LoginRequest request = new LoginRequest("pro@refiq.com", "good_pass");
    Credential credential = new Credential(userId, "pro@refiq.com", "real_hash", Instant.now());

    when(authRateLimiter.tryConsumeLogin("pro@refiq.com")).thenReturn(true);
    when(credentialRepository.findByEmail("pro@refiq.com")).thenReturn(Optional.of(credential));
    when(passwordEncoder.matches("good_pass", "real_hash")).thenReturn(true);
    when(jwtProvider.generateToken(userId)).thenReturn("mocked.jwt.token");

    // WHEN
    LoginResult result = authService.login(request);

    // THEN
    assertThat(result).isInstanceOf(LoginResult.Success.class);

    LoginResult.Success success = (LoginResult.Success) result;
    assertThat(success.response().token()).isEqualTo("mocked.jwt.token");
    assertThat(success.response().type()).isEqualTo("Bearer");
  }
}