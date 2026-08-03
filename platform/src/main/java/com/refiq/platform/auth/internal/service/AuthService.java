package com.refiq.platform.auth.internal.service;

import com.refiq.platform.auth.api.dto.LoginRequest;
import com.refiq.platform.auth.api.dto.LoginResponse;
import com.refiq.platform.auth.api.dto.LoginResult;
import com.refiq.platform.auth.api.dto.RegisterUserRequest;
import com.refiq.platform.auth.api.dto.RegistrationResponse;
import com.refiq.platform.auth.api.dto.RegistrationResult;

import com.refiq.platform.auth.api.event.UserRegisteredEvent;
import com.refiq.platform.auth.internal.domain.Credential;
import com.refiq.platform.auth.internal.repository.CredentialRepository;
import com.refiq.platform.auth.internal.security.JwtProvider;
import com.refiq.platform.auth.internal.security.AuthRateLimiter;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
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

  private final CredentialRepository credentialRepository;

  private final PasswordEncoder passwordEncoder;

  private final JwtProvider jwtProvider;

  private final AuthRateLimiter authRateLimiter;

  private final ApplicationEventPublisher eventPublisher;


  @Transactional
  public RegistrationResult register(RegisterUserRequest request, String ipAddress) {
    // Classic SLFJ4 debugging


    log.debug("Procesando solicitud de registro para: {} desde IP: {}", request.email(), ipAddress);

    if (!authRateLimiter.tryConsumeRegister(ipAddress)) {
      AuthLogEvent.REGISTRATION_BLOCKED_RATE_LIMIT.log(log, ipAddress);
      return new RegistrationResult.TooManyRequests(
          "Demasiados intentos de registro desde tu red. Por favor, espera una hora."
      );
    }

    if (credentialRepository.existsByEmail(request.email())) {
      AuthLogEvent.REGISTRATION_FAILED_EMAIL_EXISTS.log(log, request.email());
      return new RegistrationResult.EmailAlreadyExists(request.email());
    }

    var newCredential = Credential.builder()
        .email(request.email())
        .passwordHash(passwordEncoder.encode(request.password()))
        .build();

    var saved = credentialRepository.save(newCredential);

    // EVENT STUFF
    eventPublisher.publishEvent(new UserRegisteredEvent(
        saved.getId(),
        request.name(),
        saved.getEmail()
    ));


    AuthLogEvent.USER_REGISTERED.log(log, saved.getEmail(), saved.getId());

    return new RegistrationResult.Success(
        new RegistrationResponse("Usuario registrado correctamente", saved.getId().toString())
    );
  }

  /**
   * Orchestrates the authentication flow. Marked as read-only since it does not modify the database,
   * optimizing the transaction footprint.
   *
   * @param request The DTO containing the login credentials.
   * @return A sealed {@link LoginResult} representing the business outcome of the operation.
   */
  @Transactional(readOnly = true)
  public LoginResult login(LoginRequest request) {

    log.debug("Procesando solicitud de login para: {}", request.email());

    if (!authRateLimiter.tryConsumeLogin(request.email())) {
      AuthLogEvent.LOGIN_BLOCKED_RATE_LIMIT.log(log, request.email());
      return new LoginResult.TooManyRequests(
          "Demasiados intentos fallidos. Por favor, espera 15 minutos.");
    }

    var credentialOpt = credentialRepository.findByEmail(request.email());
    if (credentialOpt.isEmpty()) {
      AuthLogEvent.LOGIN_FAILED_INVALID_CREDENTIALS.log(log, request.email());
      return new LoginResult.InvalidCredentials();
    }
    var credential = credentialOpt.get();

    // Error genérico por temas de seguridad
    if (!passwordEncoder.matches(request.password(), credential.getPasswordHash())) {
      AuthLogEvent.LOGIN_FAILED_INVALID_CREDENTIALS.log(log, request.email());
      return new LoginResult.InvalidCredentials();
    }

    String token = jwtProvider.generateToken(credential.getId());

    AuthLogEvent.LOGIN_SUCCESS.log(log, credential.getId());
    return new LoginResult.Success(new LoginResponse(token));

  }

  public void logout(UUID userId) {
    // DEBT
    // En el futuro, aquí insertaríamos el token en una lista negra (Redis).
    // Por ahora, solo dejamos constancia para la auditoría.
    AuthLogEvent.LOGOUT_SUCCESS.log(log, userId);
  }

}