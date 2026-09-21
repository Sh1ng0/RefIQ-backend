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
import com.refiq.platform.auth.internal.security.AuthRateLimiter;
import com.refiq.platform.auth.internal.security.JwtProvider;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;


/**
 * Core service orchestrating user authentication and registration workflows.
 * <p>
 * Designed with Data-Oriented Programming (DOP) principles, this service returns sealed interface
 * result types (e.g., {@link RegistrationResult}, {@link LoginResult}) instead of throwing
 * exceptions for business logic deviations.
 * </p>
 * <p>
 * <b>Concurrency & Performance:</b> To prevent database connection starvation,
 * class-level {@code @Transactional} annotations are intentionally omitted. Expensive I/O
 * operations (like rate limiting) and CPU-intensive tasks (like password hashing) are executed
 * outside transactional boundaries. Explicit transaction management via {@link TransactionTemplate}
 * is used strictly for atomic state mutations and outbox event publication.
 * </p>
 * <p>
 * <b>Event-Driven Architecture:</b> Acts as a publisher within the Spring Modulith
 * topology. Emits {@link UserRegisteredEvent} to notify downstream modules (e.g., User) of
 * successful registrations asynchronously.
 * </p>
 */
@Service
public class AuthService {

  private static final Logger log = LoggerFactory.getLogger(AuthService.class);

  private final DbCredentialRepository credentialRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtProvider jwtProvider;
  private final AuthRateLimiter authRateLimiter;
  private final ApplicationEventPublisher eventPublisher;


  private final TransactionTemplate transactionTemplate;

  public AuthService(DbCredentialRepository credentialRepository, PasswordEncoder passwordEncoder,
      JwtProvider jwtProvider, AuthRateLimiter authRateLimiter,
      ApplicationEventPublisher eventPublisher, TransactionTemplate transactionTemplate) {
    this.credentialRepository = credentialRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtProvider = jwtProvider;
    this.authRateLimiter = authRateLimiter;
    this.eventPublisher = eventPublisher;
    this.transactionTemplate = transactionTemplate;
  }


  /**
   * Registers a new user and provisions their initial credentials.
   * <p>
   * Executes rate-limiting and entity construction prior to acquiring a database connection. To
   * eliminate check-then-act race conditions, uniqueness validation is delegated to the database
   * engine using an atomic insert. The database transaction strictly encompasses the insertion and
   * the subsequent domain event publication to guarantee outbox pattern consistency.
   * </p>
   *
   * @param request   the payload containing registration details.
   * @param ipAddress the client's IP address used for rate limiting.
   * @return a sealed {@link RegistrationResult} indicating success or a specific business failure.
   */
  public RegistrationResult register(RegisterUserRequest request, String ipAddress) {
    new AuthLogEvent.ProcessingRegistrationRequest(request.email(), ipAddress).log(log);

    if (!authRateLimiter.tryConsumeRegister(ipAddress)) {
      new AuthLogEvent.RegistrationBlockedRateLimit(ipAddress).log(log);
      return new RegistrationResult.TooManyRequests(
          "Too many registration attempts from your network. Please wait an hour."
      );
    }

    var newCredential = Credential.createNew(
        request.email(),
        passwordEncoder.encode(request.password())
    );

    return transactionTemplate.execute(status -> {

      boolean isInserted = credentialRepository.tryInsert(newCredential);

      if (!isInserted) {
        new AuthLogEvent.RegistrationFailedEmailExists(request.email()).log(log);
        return new RegistrationResult.EmailAlreadyExists(request.email());
      }

      eventPublisher.publishEvent(new UserRegisteredEvent(
          newCredential.id(),
          request.name(),
          newCredential.email()
      ));

      new AuthLogEvent.UserRegistered(newCredential.email(), newCredential.id()).log(log);

      return new RegistrationResult.Success(
          new RegistrationResponse("User registered successfully", newCredential.id().toString())
      );
    });
  }


  /**
   * Authenticates a user and generates a session token.
   * <p>
   * Intentionally executed without a Spring transactional context. The database read operates in
   * auto-commit mode, ensuring the connection is immediately released before executing the
   * CPU-heavy password hashing verification.
   * </p>
   *
   * @param request the payload containing the user's email and raw password.
   * @return a sealed {@link LoginResult} containing the JWT on success, or failure details.
   */
  public LoginResult login(LoginRequest request) {
    new AuthLogEvent.ProcessingLoginRequest(request.email()).log(log);

    if (!authRateLimiter.tryConsumeLogin(request.email())) {
      new AuthLogEvent.LoginBlockedRateLimit(request.email()).log(log);
      return new LoginResult.TooManyRequests(
          "Too many attempts, please wait 15 minutes");
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

  /**
   * Terminates the user's session and emits an audit trail event.
   *
   * @param userId the unique identifier of the user logging out.
   */
  public void logout(UUID userId) {
    new AuthLogEvent.LogoutSuccess(userId).log(log);
  }
}