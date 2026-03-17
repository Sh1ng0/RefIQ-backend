package com.refiq.platform.auth.api.web;


import com.refiq.platform.auth.api.dto.LoginRequest;
import com.refiq.platform.auth.api.dto.LoginResult;
import com.refiq.platform.auth.api.dto.RegisterUserRequest;
import com.refiq.platform.auth.api.dto.RegistrationResult;
import com.refiq.platform.auth.internal.service.AuthService;

import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;


/**
 * REST controller responsible for user authentication and registration management.
 * <p>
 * Serves as the public entry point for account creation and session initialization
 * within the RefIQ platform. Relies on the {@link AuthService} for business logic execution.
 * </p>
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Profile({"!test", "security"})
public class AuthController {

  private final AuthService authService;

  /**
   * Registers a new user in the platform.
   * <p>
   * Processes the validated JSON request, delegates to the service layer, and maps the
   * resulting sealed business state to the appropriate HTTP response using pattern matching:
   * <ul>
   * <li><b>200 OK:</b> User successfully created.</li>
   * <li><b>409 Conflict:</b> The provided email is already in use.</li>
   * <li><b>400 Bad Request:</b> Invalid input data (handled globally by the validation framework).</li>
   * </ul>
   * </p>
   *
   * @param request The DTO containing the user's email and password (enforces regex validation).
   * @return A {@link ResponseEntity} containing the operation result or a conflict error message.
   */
  // With Java 21 Jackson converts naturally any incoming JSON into a record leveraging the canonical constructor
  @PostMapping("/register")
  public ResponseEntity<?> register(@RequestBody @Valid RegisterUserRequest request) {

    RegistrationResult result = authService.register(request);

    return switch (result) {

      case RegistrationResult.Success s -> ResponseEntity.ok(s.response());

      case RegistrationResult.EmailAlreadyExists e -> ResponseEntity.status(409)
          .body(Map.of("error", "El email " + e.email() + " ya está registrado."));
    };
  }

  /**
   * Authenticates a user and issues a JWT if the provided credentials are valid.
   * <p>
   * Delegates authentication to the service layer and maps the exhaustive business
   * result to the appropriate HTTP response using pattern matching.
   * </p>
   *
   * @param request The DTO containing the user's email and password.
   * @return A {@link ResponseEntity} containing the JWT (200 OK), or the corresponding error
   * (401 Unauthorized or 429 Too Many Requests).
   */
  @PostMapping("/login")
  public ResponseEntity<?> login(@RequestBody @Valid LoginRequest request) {

    LoginResult result = authService.login(request);

    return switch (result) {
      case LoginResult.Success s ->
          ResponseEntity.ok(s.response());

      case LoginResult.InvalidCredentials ic ->
          ResponseEntity.status(HttpStatus.UNAUTHORIZED)
              .body(Map.of("error", "Credenciales inválidas. Comprueba tu email y contraseña."));

      case LoginResult.TooManyRequests tmr ->
          ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
              .body(Map.of("error", tmr.message()));
    };
  }


  /**
   * Logs out the authenticated user.
   * <p>
   * Due to the stateless architecture, this endpoint primarily serves to record an audit
   * log of the voluntary logout event. The actual session invalidation must be handled
   * by the client (e.g., by removing the token from local storage).
   * </p>
   *
   * @param authentication The current Spring Security authentication token containing the user's UUID.
   * @return A 200 OK empty response.
   */
  @PostMapping("/logout")
  public ResponseEntity<Void> logout(Authentication authentication) {
    // Al pasar por el filtro, Spring ya inyectó el UUID en el Principal
    if (authentication != null && authentication.getPrincipal() instanceof UUID userId) {
      authService.logout(userId);
    }
    return ResponseEntity.ok().build();
  }
}