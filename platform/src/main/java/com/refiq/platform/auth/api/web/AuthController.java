package com.refiq.platform.auth.api.web;


import com.refiq.platform.auth.api.dto.LoginRequest;
import com.refiq.platform.auth.api.dto.LoginResult;
import com.refiq.platform.auth.api.dto.RegisterUserRequest;
import com.refiq.platform.auth.api.dto.RegistrationResult;
import com.refiq.platform.auth.api.web.response.LoginWebResponse;
import com.refiq.platform.auth.api.web.response.RegistrationWebResponse;
import com.refiq.platform.auth.internal.service.AuthService;

import com.refiq.platform.shared.web.ApiError;
import jakarta.servlet.http.HttpServletRequest;
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
public class AuthController implements AuthApi{

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
  public ResponseEntity<RegistrationWebResponse> register(@RequestBody @Valid RegisterUserRequest request, HttpServletRequest httpRequest) {


    String ipAddress = httpRequest.getHeader("X-Forwarded-For");
    if (ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
      ipAddress = httpRequest.getRemoteAddr();
    } else {
      ipAddress = ipAddress.split(",")[0].trim();
    }


    RegistrationResult result = authService.register(request, ipAddress);



    return switch (result) {

      case RegistrationResult.Success s -> ResponseEntity.ok(new RegistrationWebResponse.Success(s.response()));

      case RegistrationResult.EmailAlreadyExists e ->
          ResponseEntity.status(HttpStatus.CONFLICT)
              .body(new RegistrationWebResponse.Failure(
                  new ApiError("El email " + e.email() + " ya está registrado.")
              ));

      case RegistrationResult.TooManyRequests tmr ->
          ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
              .body(new RegistrationWebResponse.Failure(
                  new ApiError(tmr.message())
              ));
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
  public ResponseEntity<LoginWebResponse> login(@RequestBody @Valid LoginRequest request) {

    LoginResult result = authService.login(request);

    return switch (result) {
      case LoginResult.Success s ->
          ResponseEntity.ok(new LoginWebResponse.Success(s.response()));

      case LoginResult.InvalidCredentials e ->
          ResponseEntity.status(HttpStatus.UNAUTHORIZED)
              .body(new LoginWebResponse.Failure(
                  new ApiError("Credenciales inválidas. Comprueba tu email y contraseña.")
              ));

      case LoginResult.TooManyRequests tmr ->
          ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
              .body(new LoginWebResponse.Failure(
                  new ApiError("Has superado el número máximo de intentos permitidos.")
              ));
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
    // DEBT
    // Mirar como gestionar esto para auditoría del logout
    if (authentication != null && authentication.getPrincipal() instanceof UUID userId) {
      authService.logout(userId);
    }
    return ResponseEntity.ok().build();
  }

  // DEBT
  // Look into how to manage this for logout auditing in the future.
}