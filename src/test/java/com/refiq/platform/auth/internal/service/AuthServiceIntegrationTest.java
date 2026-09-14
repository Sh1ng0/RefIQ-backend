package com.refiq.platform.auth.internal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.refiq.platform.auth.api.dto.login.LoginRequest;
import com.refiq.platform.auth.api.dto.login.LoginResult;
import com.refiq.platform.auth.api.dto.registration.RegisterUserRequest;
import com.refiq.platform.auth.api.dto.registration.RegistrationResult;
import com.refiq.platform.auth.internal.domain.Credential;
import com.refiq.platform.auth.internal.repository.DbCredentialRepository;
import com.refiq.platform.auth.internal.security.AuthRateLimiter;
import com.refiq.platform.auth.internal.security.JwtProvider;
import com.refiq.platform.support.slices.BasePostgresTest;
import com.refiq.platform.support.slices.RefiqModuleTest;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@RefiqModuleTest
@DisplayName("Auth - Service Layer (Postgres Integration)")
class AuthServiceIntegrationTest extends BasePostgresTest {

  @Autowired
  private AuthService authService;

  @Autowired
  private DbCredentialRepository credentialRepository;

  @Autowired
  private PasswordEncoder passwordEncoder;

  @MockitoBean
  private AuthRateLimiter authRateLimiter;

  @MockitoBean
  private JwtProvider jwtProvider;

  @BeforeEach
  void setUp() {
    when(authRateLimiter.tryConsumeRegister(anyString())).thenReturn(true);
    when(authRateLimiter.tryConsumeLogin(anyString())).thenReturn(true);
  }

  @Test
  @DisplayName("Should register a user, persist credentials, and publish a domain event")
  void shouldRegisterUserAndPersistCredentials(org.springframework.modulith.test.PublishedEvents events) {
    RegisterUserRequest request = new RegisterUserRequest("Lab Data", "dbtest@refiq.com", "Password123!");
    String ipAddress = "192.168.1.100";

    RegistrationResult result = authService.register(request, ipAddress);

    assertThat(result).isInstanceOf(RegistrationResult.Success.class);

    Optional<Credential> savedCredential = credentialRepository.findByEmail("dbtest@refiq.com");
    assertThat(savedCredential).isPresent();
    assertThat(passwordEncoder.matches("Password123!", savedCredential.get().passwordHash())).isTrue();

    var publishedEvents = events.ofType(com.refiq.platform.auth.api.event.UserRegisteredEvent.class);
    assertThat(publishedEvents).hasSize(1);

    var event = publishedEvents.iterator().next();
    assertThat(event.contactEmail()).isEqualTo("dbtest@refiq.com");
    assertThat(event.userName()).isEqualTo("Lab Data");
    assertThat(event.accountId()).isEqualTo(savedCredential.get().id());
  }

  @Test
  @DisplayName("Should return EmailAlreadyExists if the email is already in the database")
  void shouldReturnEmailAlreadyExists() {
    Credential existingUser = new Credential(UUID.randomUUID(), "duplicate@refiq.com", passwordEncoder.encode("oldPassword!"), Instant.now());
    credentialRepository.insert(existingUser);

    RegisterUserRequest request = new RegisterUserRequest("New Lab", "duplicate@refiq.com", "newPassword!");

    RegistrationResult result = authService.register(request, "127.0.0.1");

    assertThat(result).isInstanceOf(RegistrationResult.EmailAlreadyExists.class);
  }

  @Test
  @DisplayName("Should block registration if the rate limiter threshold is exceeded")
  void shouldBlockRegistrationOnRateLimitExceeded() {
    when(authRateLimiter.tryConsumeRegister("10.0.0.1")).thenReturn(false);
    RegisterUserRequest request = new RegisterUserRequest("Spam", "spam@refiq.com", "Pass123!");

    RegistrationResult result = authService.register(request, "10.0.0.1");

    assertThat(result).isInstanceOf(RegistrationResult.TooManyRequests.class);
    assertThat(credentialRepository.findByEmail("spam@refiq.com")).isEmpty();
  }

  @Test
  @DisplayName("Should authenticate correctly with valid credentials")
  void shouldLoginSuccessfully() {
    Credential user = new Credential(UUID.randomUUID(), "login@refiq.com", passwordEncoder.encode("CorrectPass123!"), Instant.now());
    credentialRepository.insert(user);

    when(jwtProvider.generateToken(any(UUID.class))).thenReturn("mocked.jwt.token");

    LoginRequest request = new LoginRequest("login@refiq.com", "CorrectPass123!");

    LoginResult result = authService.login(request);

    assertThat(result).isInstanceOf(LoginResult.Success.class);
    LoginResult.Success successResult = (LoginResult.Success) result;
    assertThat(successResult.response().token()).isEqualTo("mocked.jwt.token");
  }

  @Test
  @DisplayName("Should return InvalidCredentials if the password does not match")
  void shouldReturnInvalidCredentialsOnWrongPassword() {
    Credential user = new Credential(UUID.randomUUID(), "secure@refiq.com", passwordEncoder.encode("RealPassword!"), Instant.now());
    credentialRepository.insert(user);

    LoginRequest request = new LoginRequest("secure@refiq.com", "WrongPassword!");

    LoginResult result = authService.login(request);

    assertThat(result).isInstanceOf(LoginResult.InvalidCredentials.class);
  }
}