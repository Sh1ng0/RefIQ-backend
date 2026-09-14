package com.refiq.platform.auth.api.web;

import com.refiq.platform.auth.api.dto.login.LoginRequest;
import com.refiq.platform.auth.api.dto.login.LoginResult;
import com.refiq.platform.auth.api.dto.registration.RegisterUserRequest;
import com.refiq.platform.auth.api.dto.registration.RegistrationResult;
import com.refiq.platform.auth.api.web.response.LoginWebResponse;
import com.refiq.platform.auth.api.web.response.RegistrationWebResponse;
import com.refiq.platform.auth.internal.service.AuthService;
import com.refiq.platform.shared.web.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * Manages user authentication and registration HTTP requests.
 * <p>
 * Serves as the public entry point for account creation and session initialization
 * within the platform. Relies on the {@link AuthService} for business logic execution.
 * </p>
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AuthController implements AuthApi {

  private final AuthService authService;

  /**
   * Registers a new user in the platform.
   * <p>
   * Processes the validated JSON request, delegates to the service layer, and maps the
   * resulting sealed business state to the appropriate HTTP response using pattern matching.
   * </p>
   *
   * @param request The DTO containing the user's email and password.
   * @param httpRequest The underlying HTTP request, used to extract the client IP.
   * @return A {@link ResponseEntity} containing the operation result or a conflict error message.
   */
  // With Java 21 Jackson converts naturally any incoming JSON into a record leveraging the canonical constructor
  @PostMapping("/register")
  public ResponseEntity<RegistrationWebResponse> register(
      @RequestBody @Valid RegisterUserRequest request,
      HttpServletRequest httpRequest) {

    String ipAddress = httpRequest.getHeader("X-Forwarded-For");
    if (ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
      ipAddress = httpRequest.getRemoteAddr();
    } else {
      ipAddress = ipAddress.split(",")[0].trim();
    }

    RegistrationResult result = authService.register(request, ipAddress);

    return switch (result) {
      case RegistrationResult.Success s ->
          ResponseEntity.ok(new RegistrationWebResponse.Success(s.response()));

      case RegistrationResult.EmailAlreadyExists e ->
          ResponseEntity.status(HttpStatus.CONFLICT)
              .body(new RegistrationWebResponse.Failure(
                  new ApiError("The email " + e.email() + " is already registered.")
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
   * @return A {@link ResponseEntity} containing the JWT (200 OK), or the corresponding error.
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
                  new ApiError("Invalid credentials. Please check your email and password.")
              ));

      case LoginResult.TooManyRequests tmr ->
          ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
              .body(new LoginWebResponse.Failure(
                  new ApiError("You have exceeded the maximum allowed login attempts.")
              ));
    };
  }

  /**
   * Logs out the authenticated user.
   * <p>
   * Due to the stateless architecture, this endpoint primarily serves to record an audit
   * log of the voluntary logout event.
   * </p>
   *
   * @param authentication The current Spring Security authentication token containing the user's UUID.
   * @return A 200 OK empty response.
   */
  @PostMapping("/logout")
  public ResponseEntity<Void> logout(Authentication authentication) {
    // DEBT: Look into how to manage this for logout auditing in the future.
    if (authentication != null && authentication.getPrincipal() instanceof UUID userId) {
      authService.logout(userId);
    }
    return ResponseEntity.ok().build();
  }
}