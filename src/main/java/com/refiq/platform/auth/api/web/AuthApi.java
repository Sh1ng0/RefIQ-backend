package com.refiq.platform.auth.api.web;

import com.refiq.platform.auth.api.dto.LoginRequest;
import com.refiq.platform.auth.api.dto.RegisterUserRequest;
import com.refiq.platform.auth.api.web.response.LoginWebResponse;
import com.refiq.platform.auth.api.web.response.RegistrationWebResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@RequestMapping("/api")
@Tag(name = "Authentication Module", description = "Endpoints for registration, login, and session management")
public interface AuthApi {

  @Operation(
      summary = "Registers a new user",
      description = "Creates a new user account by validating password robustness, email availability, and limiting abusive attempts per IP."
  )
  @ApiResponses(value = {
      @ApiResponse(
          responseCode = "200",
          description = "User successfully registered",
          content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = RegistrationWebResponse.Success.class))
      ),
      @ApiResponse(
          responseCode = "400",
          description = "Input data validation error",
          content = @Content(
              mediaType = MediaType.APPLICATION_JSON_VALUE,
              schema = @Schema(implementation = RegistrationWebResponse.Failure.class),
              examples = @ExampleObject(
                  name = "ValidationError",
                  value = "{\n  \"error\": {\n    \"error\": \"Validation error\",\n    \"details\": {\n      \"password\": \"Password must be robust (min 8 characters, uppercase, number, and symbol)\"\n    }\n  }\n}"
              )
          )
      ),
      @ApiResponse(
          responseCode = "409",
          description = "Conflict: Email is already registered",
          content = @Content(
              mediaType = MediaType.APPLICATION_JSON_VALUE,
              schema = @Schema(implementation = RegistrationWebResponse.Failure.class),
              examples = @ExampleObject(
                  name = "EmailConflict",
                  value = "{\n  \"error\": {\n    \"error\": \"The email user@refiq.com is already registered.\",\n    \"details\": null\n  }\n}"
              )
          )
      ),
      @ApiResponse(
          responseCode = "429",
          description = "Too many registration attempts from the same IP (Rate limiting)",
          content = @Content(
              mediaType = MediaType.APPLICATION_JSON_VALUE,
              schema = @Schema(implementation = RegistrationWebResponse.Failure.class),
              examples = @ExampleObject(
                  name = "TooManyRequests",
                  value = "{\n  \"error\": {\n    \"error\": \"Too many registration attempts from this network. Please try again in an hour.\",\n    \"details\": null\n  }\n}"
              )
          )
      )
  })
  @PostMapping(value = "/register", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
  ResponseEntity<RegistrationWebResponse> register(
      @RequestBody @Valid RegisterUserRequest request,
      @Parameter(hidden = true) HttpServletRequest httpRequest
  );

  @Operation(
      summary = "Authenticates a user",
      description = "Validates user credentials and returns a JWT for the session."
  )
  @ApiResponses(value = {
      @ApiResponse(
          responseCode = "200",
          description = "Successful authentication",
          content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = LoginWebResponse.Success.class))
      ),
      @ApiResponse(
          responseCode = "400",
          description = "Input data validation error",
          content = @Content(
              mediaType = MediaType.APPLICATION_JSON_VALUE,
              schema = @Schema(implementation = LoginWebResponse.Failure.class)
          )
      ),
      @ApiResponse(
          responseCode = "401",
          description = "Invalid credentials",
          content = @Content(
              mediaType = MediaType.APPLICATION_JSON_VALUE,
              schema = @Schema(implementation = LoginWebResponse.Failure.class),
              examples = @ExampleObject(
                  name = "InvalidCredentials",
                  value = "{\n  \"error\": {\n    \"error\": \"Invalid credentials. Please check your email and password.\",\n    \"details\": null\n  }\n}"
              )
          )
      ),
      @ApiResponse(
          responseCode = "429",
          description = "Too many login attempts (Rate limiting)",
          content = @Content(
              mediaType = MediaType.APPLICATION_JSON_VALUE,
              schema = @Schema(implementation = LoginWebResponse.Failure.class),
              examples = @ExampleObject(
                  name = "TooManyRequests",
                  value = "{\n  \"error\": {\n    \"error\": \"You have exceeded the maximum allowed login attempts.\",\n    \"details\": null\n  }\n}"
              )
          )
      )
  })
  @PostMapping(value = "/login", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
  ResponseEntity<LoginWebResponse> login(@RequestBody @Valid LoginRequest request);
}