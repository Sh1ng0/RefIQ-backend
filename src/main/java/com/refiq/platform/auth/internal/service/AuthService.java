package com.refiq.platform.auth.internal.service;

import com.refiq.platform.auth.api.dto.login.LoginRequest;
import com.refiq.platform.auth.api.dto.login.LoginResponse;
import com.refiq.platform.auth.api.dto.login.LoginResult;
import com.refiq.platform.auth.api.dto.registration.RegisterUserRequest;
import com.refiq.platform.auth.api.dto.registration.RegistrationResponse;
import com.refiq.platform.auth.api.dto.registration.RegistrationResult;
import com.refiq.platform.auth.api.event.UserRegisteredEvent;
import com.refiq.platform.auth.internal.domain.Credential;
import com.refiq.platform.auth.internal.logging.AuthLogEvent;
import com.refiq.platform.auth.internal.repository.DbCredentialRepository;
import com.refiq.platform.auth.internal.security.JwtProvider;
import com.refiq.platform.auth.internal.security.AuthRateLimiter;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Core service responsible for orchestrating user authentication and registration workflows.
 * <p>
 * This service operates within a stateless architecture, utilizing JSON Web Tokens (JWT) for
 * authorization. It strictly adheres to Data-Oriented Programming (DOP) principles by returning
 * sealed interface result types (e.g., {@link RegistrationResult}, {@link LoginResult}) instead of
 * throwing exceptions for business logic deviations.
 * </p>
 * <p>
 * <b>Event-Driven Architecture:</b> This service acts as a primary Publisher. Upon successful
 * user registration, it broadcasts a {@link UserRegisteredEvent} to the application context.
 * This allows other business modules (e.g., the User module) to react asynchronously without
 * creating tight structural coupling.
 * </p>
 */
@Service
@RequiredArgsConstructor
public class AuthService {

  private static final Logger log = LoggerFactory.getLogger(AuthService.class);

  private final DbCredentialRepository credentialRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtProvider jwtProvider;
  private final AuthRateLimiter authRateLimiter;
  private final ApplicationEventPublisher eventPublisher;

  @Transactional
  public RegistrationResult register(RegisterUserRequest request, String ipAddress) {
    new AuthLogEvent.ProcessingRegistrationRequest(request.email(), ipAddress).log(log);

    if (!authRateLimiter.tryConsumeRegister(ipAddress)) {
      new AuthLogEvent.RegistrationBlockedRateLimit(ipAddress).log(log);
      return new RegistrationResult.TooManyRequests(
          "Demasiados intentos de registro desde tu red. Por favor, espera una hora."
      );
    }

    if (credentialRepository.existsByEmail(request.email())) {
      new AuthLogEvent.RegistrationFailedEmailExists(request.email()).log(log);
      return new RegistrationResult.EmailAlreadyExists(request.email());
    }

    var newCredential = Credential.createNew(
        request.email(),
        passwordEncoder.encode(request.password())
    );

    credentialRepository.insert(newCredential);

    // EVENT STUFF
    eventPublisher.publishEvent(new UserRegisteredEvent(
        newCredential.id(),
        request.name(),
        newCredential.email()
    ));

    new AuthLogEvent.UserRegistered(newCredential.email(), newCredential.id()).log(log);

    return new RegistrationResult.Success(
        new RegistrationResponse("Usuario registrado correctamente", newCredential.id().toString())
    );
  }

  @Transactional(readOnly = true)
  public LoginResult login(LoginRequest request) {
    new AuthLogEvent.ProcessingLoginRequest(request.email()).log(log);

    if (!authRateLimiter.tryConsumeLogin(request.email())) {
      new AuthLogEvent.LoginBlockedRateLimit(request.email()).log(log);
      return new LoginResult.TooManyRequests(
          "Demasiados intentos fallidos. Por favor, espera 15 minutos.");
    }

    var credentialOpt = credentialRepository.findByEmail(request.email());
    if (credentialOpt.isEmpty()) {
      new AuthLogEvent.LoginFailedInvalidCredentials(request.email()).log(log);
      return new LoginResult.InvalidCredentials();
    }

    var credential = credentialOpt.get();

    if (!passwordEncoder.matches(request.password(), credential.passwordHash())) {
      new AuthLogEvent.LoginFailedInvalidCredentials(request.email()).log(log);
      return new LoginResult.InvalidCredentials();
    }

    String token = jwtProvider.generateToken(credential.id());

    new AuthLogEvent.LoginSuccess(credential.id()).log(log);
    return new LoginResult.Success(new LoginResponse(token));
  }

  public void logout(UUID userId) {
    new AuthLogEvent.LogoutSuccess(userId).log(log);
  }
}