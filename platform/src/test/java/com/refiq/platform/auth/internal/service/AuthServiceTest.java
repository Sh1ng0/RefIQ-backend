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
import com.refiq.platform.auth.internal.domain.Credential;
import com.refiq.platform.auth.internal.repository.CredentialRepository;
import com.refiq.platform.auth.internal.security.JwtProvider;
import com.refiq.platform.auth.internal.security.LoginRateLimiter;
import com.refiq.platform.auth.internal.service.AuthService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService - Unit Tests")
class AuthServiceTest {

  @Mock
  private CredentialRepository credentialRepository;

  @Mock
  private PasswordEncoder passwordEncoder;

  @Mock
  private JwtProvider jwtProvider;

  @Mock
  private LoginRateLimiter loginRateLimiter;

  @InjectMocks
  private AuthService authService;

  // --- TESTS DE REGISTRO ---

  @Test
  @DisplayName("Registro: Falla si el email ya existe")
  void register_ShouldReturnEmailAlreadyExists_WhenEmailIsInDb() {
    // GIVEN
    RegisterUserRequest request = new RegisterUserRequest("test@refiq.com", "Password123!");
    when(credentialRepository.existsByEmail("test@refiq.com")).thenReturn(true);

    // WHEN
    RegistrationResult result = authService.register(request);

    // THEN
    assertThat(result).isInstanceOf(RegistrationResult.EmailAlreadyExists.class);
    verify(credentialRepository, never()).save(any()); // Nos aseguramos de que no intente guardar
  }

  @Test
  @DisplayName("Registro: Exitoso, hashea la contraseña y devuelve Success")
  void register_ShouldHashPasswordAndReturnSuccess() {
    // GIVEN
    RegisterUserRequest request = new RegisterUserRequest("nuevo@refiq.com", "Password123!");
    UUID fakeId = UUID.randomUUID();

    when(credentialRepository.existsByEmail("nuevo@refiq.com")).thenReturn(false);
    when(passwordEncoder.encode("Password123!")).thenReturn("hashed_password");

    // Simulamos el guardado devolviendo una entidad con ID
    when(credentialRepository.save(any(Credential.class))).thenAnswer(invocation -> {
      Credential c = invocation.getArgument(0);
      c.setId(fakeId);
      return c;
    });

    // WHEN
    RegistrationResult result = authService.register(request);

    // THEN
    assertThat(result).isInstanceOf(RegistrationResult.Success.class);

    RegistrationResult.Success success = (RegistrationResult.Success) result;
    assertThat(success.response().userId()).isEqualTo(fakeId.toString());

    // Verificamos que se guardó con la contraseña hasheada, NUNCA en texto plano
    ArgumentCaptor<Credential> credentialCaptor = ArgumentCaptor.forClass(Credential.class);
    verify(credentialRepository).save(credentialCaptor.capture());
    assertThat(credentialCaptor.getValue().getPasswordHash()).isEqualTo("hashed_password");
  }

  // --- TESTS DE LOGIN ---

  @Test
  @DisplayName("Login: Bloqueado por Rate Limiting antes de tocar la BD")
  void login_ShouldReturnTooManyRequests_WhenRateLimiterBlocks() {
    // GIVEN
    LoginRequest request = new LoginRequest("spammer@refiq.com", "pass");
    when(loginRateLimiter.tryConsume("spammer@refiq.com")).thenReturn(false);

    // WHEN
    LoginResult result = authService.login(request);

    // THEN
    assertThat(result).isInstanceOf(LoginResult.TooManyRequests.class);
    verify(credentialRepository, never()).findByEmail(anyString()); // La BD ni se entera
  }

  @Test
  @DisplayName("Login: Falla por credenciales inválidas (Usuario no existe)")
  void login_ShouldReturnInvalidCredentials_WhenUserNotFound() {
    // GIVEN
    LoginRequest request = new LoginRequest("fantasma@refiq.com", "pass");
    when(loginRateLimiter.tryConsume("fantasma@refiq.com")).thenReturn(true);
    when(credentialRepository.findByEmail("fantasma@refiq.com")).thenReturn(Optional.empty());

    // WHEN
    LoginResult result = authService.login(request);

    // THEN
    assertThat(result).isInstanceOf(LoginResult.InvalidCredentials.class);
  }

  @Test
  @DisplayName("Login: Falla por credenciales inválidas (Mala contraseña)")
  void login_ShouldReturnInvalidCredentials_WhenPasswordDoesNotMatch() {
    // GIVEN
    LoginRequest request = new LoginRequest("real@refiq.com", "bad_pass");
    Credential credential = Credential.builder().email("real@refiq.com").passwordHash("hash_real").build();

    when(loginRateLimiter.tryConsume("real@refiq.com")).thenReturn(true);
    when(credentialRepository.findByEmail("real@refiq.com")).thenReturn(Optional.of(credential));
    when(passwordEncoder.matches("bad_pass", "hash_real")).thenReturn(false);

    // WHEN
    LoginResult result = authService.login(request);

    // THEN
    assertThat(result).isInstanceOf(LoginResult.InvalidCredentials.class);
    verify(jwtProvider, never()).generateToken(any()); // No se genera token
  }

  @Test
  @DisplayName("Login: Exitoso, devuelve Success con el JWT")
  void login_ShouldReturnSuccessWithToken_WhenCredentialsAreValid() {
    // GIVEN
    UUID userId = UUID.randomUUID();
    LoginRequest request = new LoginRequest("pro@refiq.com", "good_pass");
    Credential credential = Credential.builder().id(userId).email("pro@refiq.com").passwordHash("hash_real").build();

    when(loginRateLimiter.tryConsume("pro@refiq.com")).thenReturn(true);
    when(credentialRepository.findByEmail("pro@refiq.com")).thenReturn(Optional.of(credential));
    when(passwordEncoder.matches("good_pass", "hash_real")).thenReturn(true);
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