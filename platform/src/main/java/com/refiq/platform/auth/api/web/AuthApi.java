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
@Tag(name = "Authentication Module", description = "Endpoints para registro, login y gestión de sesión")
public interface AuthApi {

  @Operation(
      summary = "Registrar un nuevo usuario",
      description = "Crea una nueva cuenta de usuario validando la robustez de la contraseña, la disponibilidad del email y limitando intentos abusivos por IP."
  )
  @ApiResponses(value = {
      @ApiResponse(
          responseCode = "200",
          description = "Usuario registrado con éxito",
          content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = RegistrationWebResponse.Success.class))
      ),
      @ApiResponse(
          responseCode = "400",
          description = "Error de validación en los datos de entrada",
          content = @Content(
              mediaType = MediaType.APPLICATION_JSON_VALUE,
              schema = @Schema(implementation = RegistrationWebResponse.Failure.class),
              examples = @ExampleObject(
                  name = "ValidationError",
                  value = "{\n  \"error\": {\n    \"error\": \"Error de validación\",\n    \"details\": {\n      \"password\": \"La contraseña debe ser robusta (min 8 caracteres, mayúscula, número y símbolo)\"\n    }\n  }\n}"
              )
          )
      ),
      @ApiResponse(
          responseCode = "409",
          description = "Conflicto: El email ya está registrado",
          content = @Content(
              mediaType = MediaType.APPLICATION_JSON_VALUE,
              schema = @Schema(implementation = RegistrationWebResponse.Failure.class),
              examples = @ExampleObject(
                  name = "EmailConflict",
                  value = "{\n  \"error\": {\n    \"error\": \"El email usuario@refiq.com ya está registrado.\",\n    \"details\": null\n  }\n}"
              )
          )
      ),
      @ApiResponse(
          responseCode = "429",
          description = "Demasiados intentos de registro desde la misma IP (Rate limiting)",
          content = @Content(
              mediaType = MediaType.APPLICATION_JSON_VALUE,
              schema = @Schema(implementation = RegistrationWebResponse.Failure.class),
              examples = @ExampleObject(
                  name = "TooManyRequests",
                  value = "{\n  \"error\": {\n    \"error\": \"Demasiados intentos de registro desde tu red. Por favor, espera una hora.\",\n    \"details\": null\n  }\n}"
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
      summary = "Autenticar usuario",
      description = "Valida las credenciales del usuario y devuelve un token JWT para la sesión."
  )
  @ApiResponses(value = {
      @ApiResponse(
          responseCode = "200",
          description = "Autenticación exitosa",
          content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = LoginWebResponse.Success.class))
      ),
      @ApiResponse(
          responseCode = "400",
          description = "Error de validación en los datos de entrada",
          content = @Content(
              mediaType = MediaType.APPLICATION_JSON_VALUE,
              schema = @Schema(implementation = LoginWebResponse.Failure.class)
          )
      ),
      @ApiResponse(
          responseCode = "401",
          description = "Credenciales inválidas",
          content = @Content(
              mediaType = MediaType.APPLICATION_JSON_VALUE,
              schema = @Schema(implementation = LoginWebResponse.Failure.class),
              examples = @ExampleObject(
                  name = "InvalidCredentials",
                  value = "{\n  \"error\": {\n    \"error\": \"Credenciales inválidas. Comprueba tu email y contraseña.\",\n    \"details\": null\n  }\n}"
              )
          )
      ),
      @ApiResponse(
          responseCode = "429",
          description = "Demasiados intentos de login (Rate limiting)",
          content = @Content(
              mediaType = MediaType.APPLICATION_JSON_VALUE,
              schema = @Schema(implementation = LoginWebResponse.Failure.class),
              examples = @ExampleObject(
                  name = "TooManyRequests",
                  value = "{\n  \"error\": {\n    \"error\": \"Has superado el número máximo de intentos permitidos.\",\n    \"details\": null\n  }\n}"
              )
          )
      )
  })
  @PostMapping(value = "/login", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
  ResponseEntity<LoginWebResponse> login(@RequestBody @Valid LoginRequest request);
}