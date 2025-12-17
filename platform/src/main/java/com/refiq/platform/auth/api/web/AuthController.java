package com.refiq.platform.auth.api.web;


import com.refiq.platform.auth.api.dto.RegisterUserRequest;
import com.refiq.platform.auth.api.dto.RegistrationResult;
import com.refiq.platform.auth.internal.service.AuthService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;


/**
 * Controlador REST para la gestión de autenticación y registro de usuarios.
 * <p>
 * Punto de entrada público para la creación de cuentas en la plataforma RefIQ.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AuthController {

  private final AuthService authService;

  /**
   * Registra un nuevo usuario en la plataforma.
   * <p>
   * Transforma la solicitud JSON validada en un resultado de negocio y mapea dicho resultado a la
   * respuesta HTTP adecuada:
   * <ul>
   * <li><b>200 OK:</b> Usuario creado correctamente.</li>
   * <li><b>409 Conflict:</b> El email ya está en uso.</li>
   * <li><b>400 Bad Request:</b> Datos de entrada inválidos (manejado globalmente).</li>
   * </ul>
   *
   * @param request DTO con email y contraseña (validada por regex).
   * @return ResponseEntity con el resultado de la operación o el error de conflicto.
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
}